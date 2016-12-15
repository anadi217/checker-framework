package org.checkerframework.checker.upperbound;

import java.util.List;
import java.util.Set;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.type.TypeKind;
import org.checkerframework.checker.upperbound.qual.*;
import org.checkerframework.dataflow.analysis.ConditionalTransferResult;
import org.checkerframework.dataflow.analysis.FlowExpressions;
import org.checkerframework.dataflow.analysis.FlowExpressions.Receiver;
import org.checkerframework.dataflow.analysis.RegularTransferResult;
import org.checkerframework.dataflow.analysis.TransferInput;
import org.checkerframework.dataflow.analysis.TransferResult;
import org.checkerframework.dataflow.cfg.node.*;
import org.checkerframework.framework.flow.CFAbstractTransfer;
import org.checkerframework.framework.type.QualifierHierarchy;
import org.checkerframework.javacutil.AnnotationUtils;

public class UpperBoundTransfer
        extends CFAbstractTransfer<UpperBoundValue, UpperBoundStore, UpperBoundTransfer> {

    private final AnnotationMirror UNKNOWN;

    private UpperBoundAnnotatedTypeFactory atypeFactory;

    private QualifierHierarchy qualifierHierarchy;

    public UpperBoundTransfer(UpperBoundAnalysis analysis) {
        super(analysis);
        atypeFactory = (UpperBoundAnnotatedTypeFactory) analysis.getTypeFactory();
        qualifierHierarchy = atypeFactory.getQualifierHierarchy();
        UNKNOWN = UpperBoundAnnotatedTypeFactory.UNKNOWN;
    }

    // Refine the type of expressions used as an array dimension to be
    // less than length of the array to which the new array is
    // assigned.
    @Override
    public TransferResult<UpperBoundValue, UpperBoundStore> visitAssignment(
            AssignmentNode node, TransferInput<UpperBoundValue, UpperBoundStore> in) {
        TransferResult<UpperBoundValue, UpperBoundStore> result = super.visitAssignment(node, in);

        // When an existing array is assigned into, we need to blow up the store -
        // that is, we need to remove every instance of LTL and LTEL, since arrays
        // might be aliased, and when an array is modified to be a different length,
        // that could cause any of our information about arrays to be wrong.
        if (node.getTarget().getType().getKind() == TypeKind.ARRAY) {
            // This means that the existing store needs to be invalidated.
            // As far as I can tell the easiest way to do this is to just
            // create a new TransferResult.
            TransferResult<UpperBoundValue, UpperBoundStore> newResult =
                    new RegularTransferResult<UpperBoundValue, UpperBoundStore>(
                            result.getResultValue(), new UpperBoundStore(analysis, true));
            result = newResult;
        }

        // This handles when a new array is created.
        if (node.getExpression() instanceof ArrayCreationNode) {
            ArrayCreationNode acNode = (ArrayCreationNode) node.getExpression();
            UpperBoundStore store = result.getRegularStore();
            List<Node> nodeList = acNode.getDimensions();
            if (nodeList.size() < 1) {
                return result;
            }
            Node dim = acNode.getDimension(0);
            Receiver rec = FlowExpressions.internalReprOf(analysis.getTypeFactory(), dim);
            String name = node.getTarget().toString();
            String[] names = {name};

            Set<AnnotationMirror> oldType = in.getValueOfSubNode(dim).getAnnotations();

            AnnotationMirror newType =
                    qualifierHierarchy.greatestLowerBound(
                            qualifierHierarchy.findAnnotationInHierarchy(oldType, UNKNOWN),
                            UpperBoundAnnotatedTypeFactory.createLTLengthOfAnnotation(names));

            store.insertValue(rec, newType);
        }
        return result;
    }

    /**
     * This struct contains all of the information that the refinement functions need. It's called
     * by each node function (i.e. greater than node, less than node, etc.) and then the results are
     * passed to the refinement function in whatever order is appropriate for that node. Its
     * constructor contains all of its logic. I originally wrote this for LowerBoundTransfer but I'm
     * duplicating it here since I need it again...maybe it should live elsewhere and be shared? I
     * don't know where though.
     */
    private class RefinementInfo {
        public Node left, right;
        public Set<AnnotationMirror> leftType, rightType;
        public UpperBoundStore thenStore, elseStore;
        public ConditionalTransferResult<UpperBoundValue, UpperBoundStore> newResult;

        public RefinementInfo(
                TransferResult<UpperBoundValue, UpperBoundStore> result,
                TransferInput<UpperBoundValue, UpperBoundStore> in,
                Node r,
                Node l) {
            right = r;
            left = l;

            rightType = in.getValueOfSubNode(right).getAnnotations();
            leftType = in.getValueOfSubNode(left).getAnnotations();

            thenStore = result.getRegularStore();
            elseStore = thenStore.copy();

            newResult =
                    new ConditionalTransferResult<>(result.getResultValue(), thenStore, elseStore);
        }
    }

    // So I actually just ended up copying these from Lower Bound Transfer too.
    // The only parts that are actually different are the definitions of
    // refineGT and refineGTE, and the handling of equals and not equals. The
    // code for the visitGreaterThan, visitLessThan, etc., are all identical to
    // their LBC counterparts.

    @Override
    public TransferResult<UpperBoundValue, UpperBoundStore> visitGreaterThan(
            GreaterThanNode node, TransferInput<UpperBoundValue, UpperBoundStore> in) {
        TransferResult<UpperBoundValue, UpperBoundStore> result = super.visitGreaterThan(node, in);
        RefinementInfo rfi =
                new RefinementInfo(result, in, node.getRightOperand(), node.getLeftOperand());

        // Refine the then branch.
        refineGT(rfi.left, rfi.leftType, rfi.right, rfi.rightType, rfi.thenStore);

        // Refine the else branch, which is the inverse of the then branch.
        refineGTE(rfi.right, rfi.rightType, rfi.left, rfi.leftType, rfi.elseStore);

        return rfi.newResult;
    }

    @Override
    public TransferResult<UpperBoundValue, UpperBoundStore> visitGreaterThanOrEqual(
            GreaterThanOrEqualNode node, TransferInput<UpperBoundValue, UpperBoundStore> in) {
        TransferResult<UpperBoundValue, UpperBoundStore> result =
                super.visitGreaterThanOrEqual(node, in);

        RefinementInfo rfi =
                new RefinementInfo(result, in, node.getRightOperand(), node.getLeftOperand());

        // Refine the then branch.
        refineGTE(rfi.left, rfi.leftType, rfi.right, rfi.rightType, rfi.thenStore);

        // Refine the else branch.
        refineGT(rfi.right, rfi.rightType, rfi.left, rfi.leftType, rfi.elseStore);

        return rfi.newResult;
    }

    @Override
    public TransferResult<UpperBoundValue, UpperBoundStore> visitLessThanOrEqual(
            LessThanOrEqualNode node, TransferInput<UpperBoundValue, UpperBoundStore> in) {
        TransferResult<UpperBoundValue, UpperBoundStore> result =
                super.visitLessThanOrEqual(node, in);

        RefinementInfo rfi =
                new RefinementInfo(result, in, node.getRightOperand(), node.getLeftOperand());

        // Refine the then branch. A <= is just a flipped >=.
        refineGTE(rfi.right, rfi.rightType, rfi.left, rfi.leftType, rfi.thenStore);

        // Refine the else branch.
        refineGT(rfi.left, rfi.leftType, rfi.right, rfi.rightType, rfi.elseStore);
        return rfi.newResult;
    }

    @Override
    public TransferResult<UpperBoundValue, UpperBoundStore> visitLessThan(
            LessThanNode node, TransferInput<UpperBoundValue, UpperBoundStore> in) {
        TransferResult<UpperBoundValue, UpperBoundStore> result = super.visitLessThan(node, in);

        RefinementInfo rfi =
                new RefinementInfo(result, in, node.getRightOperand(), node.getLeftOperand());

        // Refine the then branch. A < is just a flipped >.
        refineGT(rfi.right, rfi.rightType, rfi.left, rfi.leftType, rfi.thenStore);

        // Refine the else branch.
        refineGTE(rfi.left, rfi.leftType, rfi.right, rfi.rightType, rfi.elseStore);
        return rfi.newResult;
    }

    @Override
    public TransferResult<UpperBoundValue, UpperBoundStore> visitEqualTo(
            EqualToNode node, TransferInput<UpperBoundValue, UpperBoundStore> in) {
        TransferResult<UpperBoundValue, UpperBoundStore> result = super.visitEqualTo(node, in);

        RefinementInfo rfi =
                new RefinementInfo(result, in, node.getRightOperand(), node.getLeftOperand());

        /*  In an ==, we only can make conclusions about the then
         *  branch (i.e. when they are, actually, equal).
         */
        refineEq(rfi.left, rfi.leftType, rfi.right, rfi.rightType, rfi.thenStore);
        return rfi.newResult;
    }

    @Override
    public TransferResult<UpperBoundValue, UpperBoundStore> visitNotEqual(
            NotEqualNode node, TransferInput<UpperBoundValue, UpperBoundStore> in) {
        TransferResult<UpperBoundValue, UpperBoundStore> result = super.visitNotEqual(node, in);

        RefinementInfo rfi =
                new RefinementInfo(result, in, node.getRightOperand(), node.getLeftOperand());

        /* != is equivalent to == and implemented the same way, but we
         * only have information about the else branch (i.e. when they are
         * not equal).
         */
        refineEq(rfi.left, rfi.leftType, rfi.right, rfi.rightType, rfi.elseStore);
        return rfi.newResult;
    }

    /**
     * The implementation of the algorithm for refining a &gt; test. If an LTEL is greater than
     * something, then that thing must be an LTL. If an LTL is greater, than the other thing must be
     * LTOM.
     */
    private void refineGT(
            Node left,
            Set<AnnotationMirror> leftType,
            Node right,
            Set<AnnotationMirror> rightType,
            UpperBoundStore store) {
        // First, check if the left type is one of the ones that tells us something.
        if (AnnotationUtils.containsSameByClass(leftType, LTEqLengthOf.class)) {
            // Create an LTL for the right type.

            Receiver rightRec = FlowExpressions.internalReprOf(analysis.getTypeFactory(), right);
            String[] names =
                    UpperBoundUtils.getValue(
                            qualifierHierarchy.findAnnotationInHierarchy(leftType, UNKNOWN));

            AnnotationMirror newType =
                    qualifierHierarchy.greatestLowerBound(
                            qualifierHierarchy.findAnnotationInHierarchy(rightType, UNKNOWN),
                            UpperBoundAnnotatedTypeFactory.createLTLengthOfAnnotation(names));

            store.insertValue(rightRec, newType);
            return;
        }
        if (AnnotationUtils.containsSameByClass(leftType, LTLengthOf.class)) {
            // Create an LTOM for the right type.

            Receiver rightRec = FlowExpressions.internalReprOf(analysis.getTypeFactory(), right);
            String[] names =
                    UpperBoundUtils.getValue(
                            qualifierHierarchy.findAnnotationInHierarchy(leftType, UNKNOWN));

            AnnotationMirror newType =
                    qualifierHierarchy.greatestLowerBound(
                            qualifierHierarchy.findAnnotationInHierarchy(rightType, UNKNOWN),
                            UpperBoundAnnotatedTypeFactory.createLTOMLengthOfAnnotation(names));

            store.insertValue(rightRec, newType);
            return;
        }
    }

    /**
     * If an LTL is greater than or equal to something, it must also be LTL. If an LTEL is greater
     * than or equal to something, it must be be LTEL. If an LTOM is gte something, that's also
     * LTOM.
     */
    private void refineGTE(
            Node left,
            Set<AnnotationMirror> leftType,
            Node right,
            Set<AnnotationMirror> rightType,
            UpperBoundStore store) {
        if (AnnotationUtils.containsSameByClass(leftType, LTLengthOf.class)) {
            // Create an LTL for the right type.
            // There's a slight danger of losing information here:
            // if the two annotations are LTL(a) and EL(b), for instance,
            // we lose some information.
            Receiver rightRec = FlowExpressions.internalReprOf(analysis.getTypeFactory(), right);
            String[] names =
                    UpperBoundUtils.getValue(
                            qualifierHierarchy.findAnnotationInHierarchy(leftType, UNKNOWN));

            AnnotationMirror newType =
                    qualifierHierarchy.greatestLowerBound(
                            qualifierHierarchy.findAnnotationInHierarchy(rightType, UNKNOWN),
                            UpperBoundAnnotatedTypeFactory.createLTLengthOfAnnotation(names));

            store.insertValue(rightRec, newType);
            return;
        } else if (AnnotationUtils.containsSameByClass(leftType, LTEqLengthOf.class)) {
            // Create an LTL for the right type.

            Receiver rightRec = FlowExpressions.internalReprOf(analysis.getTypeFactory(), right);
            String[] names =
                    UpperBoundUtils.getValue(
                            qualifierHierarchy.findAnnotationInHierarchy(leftType, UNKNOWN));

            AnnotationMirror newType =
                    qualifierHierarchy.greatestLowerBound(
                            qualifierHierarchy.findAnnotationInHierarchy(rightType, UNKNOWN),
                            UpperBoundAnnotatedTypeFactory.createLTEqLengthOfAnnotation(names));

            store.insertValue(rightRec, newType);
            return;
        } else if (AnnotationUtils.containsSameByClass(leftType, LTOMLengthOf.class)) {
            // Create an LTOM for the right type.

            Receiver rightRec = FlowExpressions.internalReprOf(analysis.getTypeFactory(), right);
            String[] names =
                    UpperBoundUtils.getValue(
                            qualifierHierarchy.findAnnotationInHierarchy(leftType, UNKNOWN));

            AnnotationMirror newType =
                    qualifierHierarchy.greatestLowerBound(
                            qualifierHierarchy.findAnnotationInHierarchy(rightType, UNKNOWN),
                            UpperBoundAnnotatedTypeFactory.createLTOMLengthOfAnnotation(names));

            store.insertValue(rightRec, newType);
            return;
        }
    }

    private void refineEq(
            Node left,
            Set<AnnotationMirror> leftType,
            Node right,
            Set<AnnotationMirror> rightType,
            UpperBoundStore store) {

        AnnotationMirror rightUpperboundType =
                qualifierHierarchy.findAnnotationInHierarchy(rightType, UNKNOWN);
        AnnotationMirror leftUpperboundType =
                qualifierHierarchy.findAnnotationInHierarchy(leftType, UNKNOWN);

        if (rightUpperboundType == null || leftUpperboundType == null) {
            return;
        }

        AnnotationMirror newType =
                qualifierHierarchy.greatestLowerBound(rightUpperboundType, leftUpperboundType);

        Receiver rightRec = FlowExpressions.internalReprOf(analysis.getTypeFactory(), right);
        Receiver leftRec = FlowExpressions.internalReprOf(analysis.getTypeFactory(), left);

        store.insertValue(rightRec, newType);
        store.insertValue(leftRec, newType);
    }
}
