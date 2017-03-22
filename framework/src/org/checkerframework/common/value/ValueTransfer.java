package org.checkerframework.common.value;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import org.checkerframework.common.value.qual.ArrayLen;
import org.checkerframework.common.value.qual.BoolVal;
import org.checkerframework.common.value.qual.BottomVal;
import org.checkerframework.common.value.qual.DoubleVal;
import org.checkerframework.common.value.qual.IntVal;
import org.checkerframework.common.value.qual.StringVal;
import org.checkerframework.common.value.qual.UnknownVal;
import org.checkerframework.common.value.util.NumberMath;
import org.checkerframework.common.value.util.NumberUtils;
import org.checkerframework.dataflow.analysis.ConditionalTransferResult;
import org.checkerframework.dataflow.analysis.FlowExpressions;
import org.checkerframework.dataflow.analysis.FlowExpressions.Receiver;
import org.checkerframework.dataflow.analysis.RegularTransferResult;
import org.checkerframework.dataflow.analysis.TransferInput;
import org.checkerframework.dataflow.analysis.TransferResult;
import org.checkerframework.dataflow.cfg.node.BitwiseAndNode;
import org.checkerframework.dataflow.cfg.node.BitwiseComplementNode;
import org.checkerframework.dataflow.cfg.node.BitwiseOrNode;
import org.checkerframework.dataflow.cfg.node.BitwiseXorNode;
import org.checkerframework.dataflow.cfg.node.ConditionalAndNode;
import org.checkerframework.dataflow.cfg.node.ConditionalNotNode;
import org.checkerframework.dataflow.cfg.node.ConditionalOrNode;
import org.checkerframework.dataflow.cfg.node.EqualToNode;
import org.checkerframework.dataflow.cfg.node.FieldAccessNode;
import org.checkerframework.dataflow.cfg.node.FloatingDivisionNode;
import org.checkerframework.dataflow.cfg.node.FloatingRemainderNode;
import org.checkerframework.dataflow.cfg.node.GreaterThanNode;
import org.checkerframework.dataflow.cfg.node.GreaterThanOrEqualNode;
import org.checkerframework.dataflow.cfg.node.IntegerDivisionNode;
import org.checkerframework.dataflow.cfg.node.IntegerRemainderNode;
import org.checkerframework.dataflow.cfg.node.LeftShiftNode;
import org.checkerframework.dataflow.cfg.node.LessThanNode;
import org.checkerframework.dataflow.cfg.node.LessThanOrEqualNode;
import org.checkerframework.dataflow.cfg.node.Node;
import org.checkerframework.dataflow.cfg.node.NotEqualNode;
import org.checkerframework.dataflow.cfg.node.NumericalAdditionNode;
import org.checkerframework.dataflow.cfg.node.NumericalMinusNode;
import org.checkerframework.dataflow.cfg.node.NumericalMultiplicationNode;
import org.checkerframework.dataflow.cfg.node.NumericalPlusNode;
import org.checkerframework.dataflow.cfg.node.NumericalSubtractionNode;
import org.checkerframework.dataflow.cfg.node.SignedRightShiftNode;
import org.checkerframework.dataflow.cfg.node.StringConcatenateAssignmentNode;
import org.checkerframework.dataflow.cfg.node.StringConcatenateNode;
import org.checkerframework.dataflow.cfg.node.StringConversionNode;
import org.checkerframework.dataflow.cfg.node.UnsignedRightShiftNode;
import org.checkerframework.dataflow.util.NodeUtils;
import org.checkerframework.framework.flow.CFAbstractAnalysis;
import org.checkerframework.framework.flow.CFStore;
import org.checkerframework.framework.flow.CFTransfer;
import org.checkerframework.framework.flow.CFValue;
import org.checkerframework.javacutil.AnnotationUtils;
import org.checkerframework.javacutil.TypesUtils;

public class ValueTransfer extends CFTransfer {
    private ValueAnnotatedTypeFactory atypefactory;

    public ValueTransfer(CFAbstractAnalysis<CFValue, CFStore, CFTransfer> analysis) {
        super(analysis);
        atypefactory = (ValueAnnotatedTypeFactory) analysis.getTypeFactory();
    }

    private List<String> getStringValues(Node subNode, TransferInput<CFValue, CFStore> p) {
        CFValue value = p.getValueOfSubNode(subNode);
        // @StringVal, @BottomVal, @UnknownVal
        AnnotationMirror numberAnno =
                AnnotationUtils.getAnnotationByClass(value.getAnnotations(), StringVal.class);
        if (numberAnno != null) {
            return AnnotationUtils.getElementValueArray(numberAnno, "value", String.class, true);
        }
        numberAnno = AnnotationUtils.getAnnotationByClass(value.getAnnotations(), UnknownVal.class);
        if (numberAnno != null) {
            return new ArrayList<String>();
        }
        numberAnno = AnnotationUtils.getAnnotationByClass(value.getAnnotations(), BottomVal.class);
        if (numberAnno != null) {
            return Collections.singletonList("null");
        }

        //@IntVal, @DoubleVal, @BoolVal (have to be converted to string)
        List<? extends Object> values;
        numberAnno = AnnotationUtils.getAnnotationByClass(value.getAnnotations(), BoolVal.class);
        if (numberAnno != null) {
            values = getBooleanValues(subNode, p);
        } else if (subNode.getType().getKind() == TypeKind.CHAR) {
            values = getCharValues(subNode, p);
        } else if (subNode instanceof StringConversionNode) {
            return getStringValues(((StringConversionNode) subNode).getOperand(), p);
        } else {
            values = getNumericalValues(subNode, p);
        }
        List<String> stringValues = new ArrayList<String>();
        for (Object o : values) {
            stringValues.add(o.toString());
        }
        return stringValues;
    }

    private List<Boolean> getBooleanValues(Node subNode, TransferInput<CFValue, CFStore> p) {
        CFValue value = p.getValueOfSubNode(subNode);
        AnnotationMirror intAnno =
                AnnotationUtils.getAnnotationByClass(value.getAnnotations(), BoolVal.class);
        return ValueAnnotatedTypeFactory.getBooleanValues(intAnno);
    }

    private List<Character> getCharValues(Node subNode, TransferInput<CFValue, CFStore> p) {
        CFValue value = p.getValueOfSubNode(subNode);
        AnnotationMirror intAnno =
                AnnotationUtils.getAnnotationByClass(value.getAnnotations(), IntVal.class);
        return ValueAnnotatedTypeFactory.getCharValues(intAnno);
    }

    private List<? extends Number> getNumericalValues(
            Node subNode, TransferInput<CFValue, CFStore> p) {
        CFValue value = p.getValueOfSubNode(subNode);
        AnnotationMirror numberAnno =
                AnnotationUtils.getAnnotationByClass(value.getAnnotations(), IntVal.class);
        List<? extends Number> values;
        if (numberAnno == null) {
            numberAnno =
                    AnnotationUtils.getAnnotationByClass(value.getAnnotations(), DoubleVal.class);
            if (numberAnno != null) {
                values =
                        AnnotationUtils.getElementValueArray(
                                numberAnno, "value", Double.class, true);
            } else {
                return new ArrayList<Number>();
            }
        } else {
            values = AnnotationUtils.getElementValueArray(numberAnno, "value", Long.class, true);
        }
        return NumberUtils.castNumbers(subNode.getType(), values);
    }

    private AnnotationMirror createStringValAnnotationMirror(List<String> values) {
        if (values.isEmpty()) {
            return atypefactory.UNKNOWNVAL;
        }
        return atypefactory.createStringAnnotation(values);
    }

    private AnnotationMirror createNumberAnnotationMirror(List<Number> values) {
        if (values.isEmpty()) {
            return atypefactory.UNKNOWNVAL;
        }
        Number first = values.get(0);
        if (first instanceof Integer || first instanceof Short || first instanceof Long) {
            List<Long> intValues = new ArrayList<>();
            for (Number number : values) {
                intValues.add(number.longValue());
            }
            return atypefactory.createIntValAnnotation(intValues);
        }
        if (first instanceof Double || first instanceof Float) {
            List<Double> intValues = new ArrayList<>();
            for (Number number : values) {
                intValues.add(number.doubleValue());
            }
            return atypefactory.createDoubleValAnnotation(intValues);
        }
        throw new UnsupportedOperationException();
    }

    private AnnotationMirror createBooleanAnnotationMirror(List<Boolean> values) {
        if (values.isEmpty()) {
            return atypefactory.UNKNOWNVAL;
        }
        return atypefactory.createBooleanAnnotation(values);
    }

    private TransferResult<CFValue, CFStore> createNewResult(
            TransferResult<CFValue, CFStore> result, List<Number> resultValues) {
        AnnotationMirror stringVal = createNumberAnnotationMirror(resultValues);
        CFValue newResultValue =
                analysis.createSingleAnnotationValue(
                        stringVal, result.getResultValue().getUnderlyingType());
        return new RegularTransferResult<>(newResultValue, result.getRegularStore());
    }

    private TransferResult<CFValue, CFStore> createNewResultBoolean(
            TransferResult<CFValue, CFStore> result,
            List<Boolean> resultValues,
            boolean isConditional) {
        AnnotationMirror boolVal = createBooleanAnnotationMirror(resultValues);
        CFValue newResultValue =
                analysis.createSingleAnnotationValue(
                        boolVal, result.getResultValue().getUnderlyingType());
        if (isConditional) {
            return new ConditionalTransferResult<>(
                    newResultValue, result.getThenStore(), result.getElseStore(), true);
        } else {
            return new RegularTransferResult<>(newResultValue, result.getRegularStore());
        }
    }

    @Override
    public TransferResult<CFValue, CFStore> visitFieldAccess(
            FieldAccessNode node, TransferInput<CFValue, CFStore> in) {

        TransferResult<CFValue, CFStore> result = super.visitFieldAccess(node, in);

        modifyArrayLenBasedOnIntValOnArrayLength(node, result.getRegularStore());

        return result;
    }

    private void modifyArrayLenBasedOnIntValOnArrayLength(
            FieldAccessNode arrayLengthNode, CFStore store) {
        // If array.length is encountered, transform its @IntVal annotation into an @ArrayLen annotation for array.
        if (NodeUtils.isArrayLengthFieldAccess(arrayLengthNode)) {
            CFValue value =
                    store.getValue(
                            FlowExpressions.internalReprOf(
                                    analysis.getTypeFactory(), arrayLengthNode));
            if (value != null) {
                AnnotationMirror lengthAnno =
                        AnnotationUtils.getAnnotationByClass(value.getAnnotations(), IntVal.class);
                if (lengthAnno != null) {
                    List<Long> lengthValues = ValueAnnotatedTypeFactory.getIntValues(lengthAnno);
                    List<Integer> arrayLenValues = new ArrayList<>(lengthValues.size());
                    for (Long l : lengthValues) {
                        arrayLenValues.add(l.intValue());
                    }
                    AnnotationMirror newArrayAnno =
                            atypefactory.createArrayLenAnnotation(arrayLenValues);
                    AnnotationMirror oldArrayAnno =
                            atypefactory.getAnnotationMirror(
                                    arrayLengthNode.getReceiver().getTree(), ArrayLen.class);
                    AnnotationMirror combinedAnno =
                            atypefactory
                                    .getQualifierHierarchy()
                                    .greatestLowerBound(oldArrayAnno, newArrayAnno);

                    Receiver arrayRec =
                            FlowExpressions.internalReprOf(
                                    analysis.getTypeFactory(), arrayLengthNode.getReceiver());
                    store.clearValue(arrayRec);
                    store.insertValue(arrayRec, combinedAnno);
                }
            }
        }
    }

    @Override
    public TransferResult<CFValue, CFStore> visitStringConcatenateAssignment(
            StringConcatenateAssignmentNode n, TransferInput<CFValue, CFStore> p) {
        TransferResult<CFValue, CFStore> result = super.visitStringConcatenateAssignment(n, p);
        return stringConcatenation(n.getLeftOperand(), n.getRightOperand(), p, result);
    }

    @Override
    public TransferResult<CFValue, CFStore> visitStringConcatenate(
            StringConcatenateNode n, TransferInput<CFValue, CFStore> p) {
        TransferResult<CFValue, CFStore> result = super.visitStringConcatenate(n, p);
        return stringConcatenation(n.getLeftOperand(), n.getRightOperand(), p, result);
    }

    public TransferResult<CFValue, CFStore> stringConcatenation(
            Node leftOperand,
            Node rightOperand,
            TransferInput<CFValue, CFStore> p,
            TransferResult<CFValue, CFStore> result) {
        List<String> lefts = getStringValues(leftOperand, p);
        List<String> rights = getStringValues(rightOperand, p);
        List<String> concat = new ArrayList<>();
        for (String left : lefts) {
            for (String right : rights) {
                concat.add(left + right);
            }
        }
        AnnotationMirror stringVal = createStringValAnnotationMirror(concat);
        TypeMirror underlyingType = result.getResultValue().getUnderlyingType();
        CFValue newResultValue = analysis.createSingleAnnotationValue(stringVal, underlyingType);
        return new RegularTransferResult<>(newResultValue, result.getRegularStore());
    }

    enum NumericalBinaryOps {
        ADDITION,
        SUBTRACTION,
        DIVISION,
        REMAINDER,
        MULTIPLICATION,
        SHIFT_LEFT,
        SIGNED_SHIFT_RIGHT,
        UNSIGNED_SHIFT_RIGHT,
        BITWISE_AND,
        BITWISE_OR,
        BITWISE_XOR;
    }

    private List<Number> calculateNumericalBinaryOp(
            Node leftNode,
            Node rightNode,
            NumericalBinaryOps op,
            TransferInput<CFValue, CFStore> p) {
        List<? extends Number> lefts = getNumericalValues(leftNode, p);
        List<? extends Number> rights = getNumericalValues(rightNode, p);
        List<Number> resultValues = new ArrayList<>();
        for (Number left : lefts) {
            NumberMath<?> nmLeft = NumberMath.getNumberMath(left);
            for (Number right : rights) {
                switch (op) {
                    case ADDITION:
                        resultValues.add(nmLeft.plus(right));
                        break;
                    case DIVISION:
                        resultValues.add(nmLeft.divide(right));
                        break;
                    case MULTIPLICATION:
                        resultValues.add(nmLeft.times(right));
                        break;
                    case REMAINDER:
                        resultValues.add(nmLeft.remainder(right));
                        break;
                    case SUBTRACTION:
                        resultValues.add(nmLeft.minus(right));
                        break;
                    case SHIFT_LEFT:
                        resultValues.add(nmLeft.shiftLeft(right));
                        break;
                    case SIGNED_SHIFT_RIGHT:
                        resultValues.add(nmLeft.signedShiftRight(right));
                        break;
                    case UNSIGNED_SHIFT_RIGHT:
                        resultValues.add(nmLeft.unsignedShiftRight(right));
                        break;
                    case BITWISE_AND:
                        resultValues.add(nmLeft.bitwiseAnd(right));
                        break;
                    case BITWISE_OR:
                        resultValues.add(nmLeft.bitwiseOr(right));
                        break;
                    case BITWISE_XOR:
                        resultValues.add(nmLeft.bitwiseXor(right));
                        break;
                    default:
                        throw new UnsupportedOperationException();
                }
            }
        }
        return resultValues;
    }

    @Override
    public TransferResult<CFValue, CFStore> visitNumericalAddition(
            NumericalAdditionNode n, TransferInput<CFValue, CFStore> p) {
        TransferResult<CFValue, CFStore> transferResult = super.visitNumericalAddition(n, p);
        List<Number> resultValues =
                calculateNumericalBinaryOp(
                        n.getLeftOperand(), n.getRightOperand(), NumericalBinaryOps.ADDITION, p);
        return createNewResult(transferResult, resultValues);
    }

    @Override
    public TransferResult<CFValue, CFStore> visitNumericalSubtraction(
            NumericalSubtractionNode n, TransferInput<CFValue, CFStore> p) {
        TransferResult<CFValue, CFStore> transferResult = super.visitNumericalSubtraction(n, p);
        List<Number> resultValues =
                calculateNumericalBinaryOp(
                        n.getLeftOperand(), n.getRightOperand(), NumericalBinaryOps.SUBTRACTION, p);
        return createNewResult(transferResult, resultValues);
    }

    @Override
    public TransferResult<CFValue, CFStore> visitNumericalMultiplication(
            NumericalMultiplicationNode n, TransferInput<CFValue, CFStore> p) {
        TransferResult<CFValue, CFStore> transferResult = super.visitNumericalMultiplication(n, p);
        List<Number> resultValues =
                calculateNumericalBinaryOp(
                        n.getLeftOperand(),
                        n.getRightOperand(),
                        NumericalBinaryOps.MULTIPLICATION,
                        p);
        return createNewResult(transferResult, resultValues);
    }

    @Override
    public TransferResult<CFValue, CFStore> visitIntegerDivision(
            IntegerDivisionNode n, TransferInput<CFValue, CFStore> p) {
        TransferResult<CFValue, CFStore> transferResult = super.visitIntegerDivision(n, p);
        List<Number> resultValues =
                calculateNumericalBinaryOp(
                        n.getLeftOperand(), n.getRightOperand(), NumericalBinaryOps.DIVISION, p);
        return createNewResult(transferResult, resultValues);
    }

    @Override
    public TransferResult<CFValue, CFStore> visitFloatingDivision(
            FloatingDivisionNode n, TransferInput<CFValue, CFStore> p) {
        TransferResult<CFValue, CFStore> transferResult = super.visitFloatingDivision(n, p);
        List<Number> resultValues =
                calculateNumericalBinaryOp(
                        n.getLeftOperand(), n.getRightOperand(), NumericalBinaryOps.DIVISION, p);
        return createNewResult(transferResult, resultValues);
    }

    @Override
    public TransferResult<CFValue, CFStore> visitIntegerRemainder(
            IntegerRemainderNode n, TransferInput<CFValue, CFStore> p) {
        TransferResult<CFValue, CFStore> transferResult = super.visitIntegerRemainder(n, p);
        List<Number> resultValues =
                calculateNumericalBinaryOp(
                        n.getLeftOperand(), n.getRightOperand(), NumericalBinaryOps.REMAINDER, p);
        return createNewResult(transferResult, resultValues);
    }

    @Override
    public TransferResult<CFValue, CFStore> visitFloatingRemainder(
            FloatingRemainderNode n, TransferInput<CFValue, CFStore> p) {
        TransferResult<CFValue, CFStore> transferResult = super.visitFloatingRemainder(n, p);
        List<Number> resultValues =
                calculateNumericalBinaryOp(
                        n.getLeftOperand(), n.getRightOperand(), NumericalBinaryOps.REMAINDER, p);
        return createNewResult(transferResult, resultValues);
    }

    @Override
    public TransferResult<CFValue, CFStore> visitLeftShift(
            LeftShiftNode n, TransferInput<CFValue, CFStore> p) {
        TransferResult<CFValue, CFStore> transferResult = super.visitLeftShift(n, p);
        List<Number> resultValues =
                calculateNumericalBinaryOp(
                        n.getLeftOperand(), n.getRightOperand(), NumericalBinaryOps.SHIFT_LEFT, p);
        return createNewResult(transferResult, resultValues);
    }

    @Override
    public TransferResult<CFValue, CFStore> visitSignedRightShift(
            SignedRightShiftNode n, TransferInput<CFValue, CFStore> p) {
        TransferResult<CFValue, CFStore> transferResult = super.visitSignedRightShift(n, p);
        List<Number> resultValues =
                calculateNumericalBinaryOp(
                        n.getLeftOperand(),
                        n.getRightOperand(),
                        NumericalBinaryOps.SIGNED_SHIFT_RIGHT,
                        p);
        return createNewResult(transferResult, resultValues);
    }

    @Override
    public TransferResult<CFValue, CFStore> visitUnsignedRightShift(
            UnsignedRightShiftNode n, TransferInput<CFValue, CFStore> p) {
        TransferResult<CFValue, CFStore> transferResult = super.visitUnsignedRightShift(n, p);
        List<Number> resultValues =
                calculateNumericalBinaryOp(
                        n.getLeftOperand(),
                        n.getRightOperand(),
                        NumericalBinaryOps.UNSIGNED_SHIFT_RIGHT,
                        p);
        return createNewResult(transferResult, resultValues);
    }

    @Override
    public TransferResult<CFValue, CFStore> visitBitwiseAnd(
            BitwiseAndNode n, TransferInput<CFValue, CFStore> p) {
        TransferResult<CFValue, CFStore> transferResult = super.visitBitwiseAnd(n, p);
        List<Number> resultValues =
                calculateNumericalBinaryOp(
                        n.getLeftOperand(), n.getRightOperand(), NumericalBinaryOps.BITWISE_AND, p);
        return createNewResult(transferResult, resultValues);
    }

    @Override
    public TransferResult<CFValue, CFStore> visitBitwiseOr(
            BitwiseOrNode n, TransferInput<CFValue, CFStore> p) {
        TransferResult<CFValue, CFStore> transferResult = super.visitBitwiseOr(n, p);
        List<Number> resultValues =
                calculateNumericalBinaryOp(
                        n.getLeftOperand(), n.getRightOperand(), NumericalBinaryOps.BITWISE_OR, p);
        return createNewResult(transferResult, resultValues);
    }

    @Override
    public TransferResult<CFValue, CFStore> visitBitwiseXor(
            BitwiseXorNode n, TransferInput<CFValue, CFStore> p) {
        TransferResult<CFValue, CFStore> transferResult = super.visitBitwiseXor(n, p);
        List<Number> resultValues =
                calculateNumericalBinaryOp(
                        n.getLeftOperand(), n.getRightOperand(), NumericalBinaryOps.BITWISE_XOR, p);
        return createNewResult(transferResult, resultValues);
    }

    enum NumericalUnaryOps {
        PLUS,
        MINUS,
        BITWISE_COMPLEMENT;
    }

    private List<Number> calculateNumericalUnaryOp(
            Node operand, NumericalUnaryOps op, TransferInput<CFValue, CFStore> p) {
        List<? extends Number> lefts = getNumericalValues(operand, p);
        List<Number> resultValues = new ArrayList<>();
        for (Number left : lefts) {
            NumberMath<?> nmLeft = NumberMath.getNumberMath(left);
            switch (op) {
                case PLUS:
                    resultValues.add(nmLeft.unaryPlus());
                    break;
                case MINUS:
                    resultValues.add(nmLeft.unaryMinus());
                    break;
                case BITWISE_COMPLEMENT:
                    resultValues.add(nmLeft.bitwiseComplement());
                    break;
                default:
                    throw new UnsupportedOperationException();
            }
        }
        return resultValues;
    }

    @Override
    public TransferResult<CFValue, CFStore> visitNumericalMinus(
            NumericalMinusNode n, TransferInput<CFValue, CFStore> p) {
        TransferResult<CFValue, CFStore> transferResult = super.visitNumericalMinus(n, p);
        List<Number> resultValues =
                calculateNumericalUnaryOp(n.getOperand(), NumericalUnaryOps.MINUS, p);
        return createNewResult(transferResult, resultValues);
    }

    @Override
    public TransferResult<CFValue, CFStore> visitNumericalPlus(
            NumericalPlusNode n, TransferInput<CFValue, CFStore> p) {
        TransferResult<CFValue, CFStore> transferResult = super.visitNumericalPlus(n, p);
        List<Number> resultValues =
                calculateNumericalUnaryOp(n.getOperand(), NumericalUnaryOps.PLUS, p);
        return createNewResult(transferResult, resultValues);
    }

    @Override
    public TransferResult<CFValue, CFStore> visitBitwiseComplement(
            BitwiseComplementNode n, TransferInput<CFValue, CFStore> p) {
        TransferResult<CFValue, CFStore> transferResult = super.visitBitwiseComplement(n, p);
        List<Number> resultValues =
                calculateNumericalUnaryOp(n.getOperand(), NumericalUnaryOps.BITWISE_COMPLEMENT, p);
        return createNewResult(transferResult, resultValues);
    }

    enum ComparisonOperators {
        EQUAL,
        NOT_EQUAL,
        GREATER_THAN,
        GREATER_THAN_EQ,
        LESS_THAN,
        LESS_THAN_EQ;
    }

    private List<Boolean> calculateBinaryComparison(
            Node leftNode,
            Node rightNode,
            ComparisonOperators op,
            TransferInput<CFValue, CFStore> p,
            TransferResult<CFValue, CFStore> result) {
        List<? extends Number> lefts = getNumericalValues(leftNode, p);
        List<? extends Number> rights = getNumericalValues(rightNode, p);
        List<Boolean> resultValues = new ArrayList<>();
        for (Number left : lefts) {
            NumberMath<?> nmLeft = NumberMath.getNumberMath(left);
            for (Number right : rights) {
                switch (op) {
                    case EQUAL:
                        resultValues.add(nmLeft.equalTo(right));
                        break;
                    case GREATER_THAN:
                        resultValues.add(nmLeft.greaterThan(right));
                        break;
                    case GREATER_THAN_EQ:
                        resultValues.add(nmLeft.greaterThanEq(right));
                        break;
                    case LESS_THAN:
                        resultValues.add(nmLeft.lessThan(right));
                        break;
                    case LESS_THAN_EQ:
                        resultValues.add(nmLeft.lessThanEq(right));
                        break;
                    case NOT_EQUAL:
                        resultValues.add(nmLeft.notEqualTo(right));
                        break;
                    default:
                        throw new UnsupportedOperationException();
                }
            }
        }

        // Refine the values of the operands in the store based on the result of the comparison.

        List<Number> thenLeftVals = new ArrayList<>();
        List<Number> elseLeftVals = new ArrayList<>();

        List<Number> thenRightVals = new ArrayList<>();
        List<Number> elseRightVals = new ArrayList<>();

        // Because resultValues contains the (boolean) results of each comparison (i.e. the product of the number
        // of elements in each list), we have to iterate through parts of the list at a time in order to find all
        // the results of comparisons involving a particular value in the left list.
        for (int i = 0; i < lefts.size(); i++) {
            for (int j = 0; j < rights.size(); j++) {
                if (resultValues.get(j + i * rights.size())) {
                    thenLeftVals.add(lefts.get(i));
                    thenRightVals.add(rights.get(j));
                } else {
                    elseLeftVals.add(lefts.get(i));
                    elseRightVals.add(rights.get(j));
                }
            }
        }

        CFStore thenStore = result.getThenStore();
        CFStore elseStore = result.getElseStore();

        createAnnotationFromResultsAndAddToStore(thenStore, thenLeftVals, leftNode);
        createAnnotationFromResultsAndAddToStore(elseStore, elseLeftVals, leftNode);

        createAnnotationFromResultsAndAddToStore(thenStore, thenRightVals, rightNode);
        createAnnotationFromResultsAndAddToStore(elseStore, elseRightVals, rightNode);

        return resultValues;
    }

    private void createAnnotationFromResultsAndAddToStore(
            CFStore store, List<?> results, Node node) {
        // If a zero-length list is passed, this returns bottom - which is very problematic.
        AnnotationMirror anno =
                atypefactory.createResultingAnnotation(
                        node.getType(), results.size() == 0 ? null : results);
        AnnotationMirror currentAnno =
                atypefactory
                        .getAnnotatedType(node.getTree())
                        .getAnnotationInHierarchy(atypefactory.BOTTOMVAL);
        Receiver rec = FlowExpressions.internalReprOf(analysis.getTypeFactory(), node);
        store.insertValue(
                rec, atypefactory.getQualifierHierarchy().greatestLowerBound(anno, currentAnno));

        if (node instanceof FieldAccessNode) {
            modifyArrayLenBasedOnIntValOnArrayLength((FieldAccessNode) node, store);
        }
    }

    @Override
    public TransferResult<CFValue, CFStore> visitLessThan(
            LessThanNode n, TransferInput<CFValue, CFStore> p) {
        TransferResult<CFValue, CFStore> transferResult = super.visitLessThan(n, p);
        ConditionalTransferResult<CFValue, CFStore> newResult =
                new ConditionalTransferResult<CFValue, CFStore>(
                        transferResult.getResultValue(),
                        transferResult.getThenStore(),
                        transferResult.getElseStore());
        List<Boolean> resultValues =
                calculateBinaryComparison(
                        n.getLeftOperand(),
                        n.getRightOperand(),
                        ComparisonOperators.LESS_THAN,
                        p,
                        newResult);
        return createNewResultBoolean(newResult, resultValues, true);
    }

    @Override
    public TransferResult<CFValue, CFStore> visitLessThanOrEqual(
            LessThanOrEqualNode n, TransferInput<CFValue, CFStore> p) {
        TransferResult<CFValue, CFStore> transferResult = super.visitLessThanOrEqual(n, p);
        ConditionalTransferResult<CFValue, CFStore> newResult =
                new ConditionalTransferResult<CFValue, CFStore>(
                        transferResult.getResultValue(),
                        transferResult.getThenStore(),
                        transferResult.getElseStore());
        List<Boolean> resultValues =
                calculateBinaryComparison(
                        n.getLeftOperand(),
                        n.getRightOperand(),
                        ComparisonOperators.LESS_THAN_EQ,
                        p,
                        newResult);
        return createNewResultBoolean(newResult, resultValues, true);
    }

    @Override
    public TransferResult<CFValue, CFStore> visitGreaterThan(
            GreaterThanNode n, TransferInput<CFValue, CFStore> p) {
        TransferResult<CFValue, CFStore> transferResult = super.visitGreaterThan(n, p);
        ConditionalTransferResult<CFValue, CFStore> newResult =
                new ConditionalTransferResult<CFValue, CFStore>(
                        transferResult.getResultValue(),
                        transferResult.getThenStore(),
                        transferResult.getElseStore());
        List<Boolean> resultValues =
                calculateBinaryComparison(
                        n.getLeftOperand(),
                        n.getRightOperand(),
                        ComparisonOperators.GREATER_THAN,
                        p,
                        newResult);
        return createNewResultBoolean(newResult, resultValues, true);
    }

    @Override
    public TransferResult<CFValue, CFStore> visitGreaterThanOrEqual(
            GreaterThanOrEqualNode n, TransferInput<CFValue, CFStore> p) {
        TransferResult<CFValue, CFStore> transferResult = super.visitGreaterThanOrEqual(n, p);
        ConditionalTransferResult<CFValue, CFStore> newResult =
                new ConditionalTransferResult<CFValue, CFStore>(
                        transferResult.getResultValue(),
                        transferResult.getThenStore(),
                        transferResult.getElseStore());
        List<Boolean> resultValues =
                calculateBinaryComparison(
                        n.getLeftOperand(),
                        n.getRightOperand(),
                        ComparisonOperators.GREATER_THAN_EQ,
                        p,
                        newResult);
        return createNewResultBoolean(newResult, resultValues, true);
    }

    @Override
    public TransferResult<CFValue, CFStore> visitEqualTo(
            EqualToNode n, TransferInput<CFValue, CFStore> p) {
        TransferResult<CFValue, CFStore> transferResult = super.visitEqualTo(n, p);
        if (TypesUtils.isPrimitive(n.getLeftOperand().getType())
                || TypesUtils.isPrimitive(n.getRightOperand().getType())) {
            // At least one must be a primitive otherwise reference equality is used.
            List<Boolean> resultValues =
                    calculateBinaryComparison(
                            n.getLeftOperand(),
                            n.getRightOperand(),
                            ComparisonOperators.EQUAL,
                            p,
                            transferResult);
            return createNewResultBoolean(transferResult, resultValues, true);
        }
        return super.visitEqualTo(n, p);
    }

    @Override
    public TransferResult<CFValue, CFStore> visitNotEqual(
            NotEqualNode n, TransferInput<CFValue, CFStore> p) {
        TransferResult<CFValue, CFStore> transferResult = super.visitNotEqual(n, p);
        if (TypesUtils.isPrimitive(n.getLeftOperand().getType())
                || TypesUtils.isPrimitive(n.getRightOperand().getType())) {
            // At least one must be a primitive otherwise reference equality is
            // used.
            List<Boolean> resultValues =
                    calculateBinaryComparison(
                            n.getLeftOperand(),
                            n.getRightOperand(),
                            ComparisonOperators.NOT_EQUAL,
                            p,
                            transferResult);
            return createNewResultBoolean(transferResult, resultValues, true);
        }
        return super.visitNotEqual(n, p);
    }

    enum ConditionalOperators {
        NOT,
        OR,
        AND;
    }

    private static final List<Boolean> ALL_BOOLEANS =
            Arrays.asList(new Boolean[] {Boolean.TRUE, Boolean.FALSE});

    private List<Boolean> calculateConditionalOperator(
            Node leftNode,
            Node rightNode,
            ConditionalOperators op,
            TransferInput<CFValue, CFStore> p) {
        List<Boolean> lefts = getBooleanValues(leftNode, p);
        if (lefts == null) {
            lefts = ALL_BOOLEANS;
        }
        List<Boolean> resultValues = new ArrayList<>();
        List<Boolean> rights = null;
        if (rightNode != null) {
            rights = getBooleanValues(rightNode, p);
            if (rights == null) {
                rights = ALL_BOOLEANS;
            }
        }
        switch (op) {
            case NOT:
                for (Boolean left : lefts) {
                    resultValues.add(!left);
                }
                return resultValues;
            case OR:
                for (Boolean left : lefts) {
                    for (Boolean right : rights) {
                        resultValues.add(left || right);
                    }
                }
                return resultValues;
            case AND:
                for (Boolean left : lefts) {
                    for (Boolean right : rights) {
                        resultValues.add(left && right);
                    }
                }
                return resultValues;
        }
        throw new RuntimeException("Unrecognized conditional operator " + op);
    }

    @Override
    public TransferResult<CFValue, CFStore> visitConditionalNot(
            ConditionalNotNode n, TransferInput<CFValue, CFStore> p) {
        TransferResult<CFValue, CFStore> transferResult = super.visitConditionalNot(n, p);
        List<Boolean> resultValues =
                calculateConditionalOperator(n.getOperand(), null, ConditionalOperators.NOT, p);
        return createNewResultBoolean(transferResult, resultValues, false);
    }

    @Override
    public TransferResult<CFValue, CFStore> visitConditionalAnd(
            ConditionalAndNode n, TransferInput<CFValue, CFStore> p) {
        TransferResult<CFValue, CFStore> transferResult = super.visitConditionalAnd(n, p);
        List<Boolean> resultValues =
                calculateConditionalOperator(
                        n.getLeftOperand(), n.getRightOperand(), ConditionalOperators.AND, p);
        return createNewResultBoolean(transferResult, resultValues, false);
    }

    @Override
    public TransferResult<CFValue, CFStore> visitConditionalOr(
            ConditionalOrNode n, TransferInput<CFValue, CFStore> p) {
        TransferResult<CFValue, CFStore> transferResult = super.visitConditionalOr(n, p);
        List<Boolean> resultValues =
                calculateConditionalOperator(
                        n.getLeftOperand(), n.getRightOperand(), ConditionalOperators.OR, p);
        return createNewResultBoolean(transferResult, resultValues, false);
    }
}
