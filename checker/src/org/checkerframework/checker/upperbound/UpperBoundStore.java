package org.checkerframework.checker.upperbound;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import javax.lang.model.element.AnnotationMirror;
import org.checkerframework.checker.upperbound.qual.*;
import org.checkerframework.dataflow.analysis.FlowExpressions;
import org.checkerframework.dataflow.analysis.FlowExpressions.FieldAccess;
import org.checkerframework.dataflow.analysis.FlowExpressions.LocalVariable;
import org.checkerframework.dataflow.analysis.FlowExpressions.Receiver;
import org.checkerframework.dataflow.cfg.node.MethodInvocationNode;
import org.checkerframework.framework.flow.CFAbstractAnalysis;
import org.checkerframework.framework.flow.CFAbstractStore;
import org.checkerframework.framework.type.AnnotatedTypeFactory;
import org.checkerframework.javacutil.AnnotationUtils;

public class UpperBoundStore extends CFAbstractStore<UpperBoundValue, UpperBoundStore> {

    protected UpperBoundStore(UpperBoundStore other) {
        super(other);
    }

    public UpperBoundStore(
            CFAbstractAnalysis<UpperBoundValue, UpperBoundStore, ?> analysis,
            boolean sequentialSemantics) {
        super(analysis, sequentialSemantics);
    }

    // If we remove from a list it reduces the minlen of anything that could be an alias of the list by 1.
    // if we clear a list anything that could be an alias of this list goes to UpperBound(0).
    @Override
    public void updateForMethodCall(
            MethodInvocationNode n, AnnotatedTypeFactory atypeFactory, UpperBoundValue val) {
        Receiver caller = FlowExpressions.internalReprOf(atypeFactory, n.getTarget().getReceiver());
        String methodName = n.getTarget().getMethod().toString();
        boolean remove = methodName.startsWith("remove(");
        boolean clear = methodName.startsWith("clear(");
        boolean add = methodName.startsWith("add(");
        Map<Receiver, UpperBoundValue> replace = new HashMap<Receiver, UpperBoundValue>();
        if (clear) {
            for (FlowExpressions.LocalVariable rec : localVariableValues.keySet()) {
                applyTransfer(rec, replace, true, atypeFactory, caller);
            }
            for (FieldAccess rec : fieldValues.keySet()) {
                applyTransfer(rec, replace, true, atypeFactory, caller);
            }
        }
        if (remove) {
            for (FlowExpressions.LocalVariable rec : localVariableValues.keySet()) {
                applyTransfer(rec, replace, false, atypeFactory, caller);
            }
            for (FieldAccess rec : fieldValues.keySet()) {
                applyTransfer(rec, replace, false, atypeFactory, caller);
            }
        }
        if (add) {
            for (FlowExpressions.LocalVariable rec : localVariableValues.keySet()) {
                applyAdd(rec, replace, atypeFactory, caller);
            }
            for (FieldAccess rec : fieldValues.keySet()) {
                applyAdd(rec, replace, atypeFactory, caller);
            }
        }
        for (Receiver rec : replace.keySet()) {
            replaceValue(rec, replace.get(rec));
        }

        super.updateForMethodCall(n, atypeFactory, val);
    }

    private void applyTransfer(
            Receiver rec,
            Map<Receiver, UpperBoundValue> replace,
            boolean isClear,
            AnnotatedTypeFactory atypeFactory,
            Receiver caller) {

        UpperBoundAnnotatedTypeFactory factory = (UpperBoundAnnotatedTypeFactory) atypeFactory;
        UpperBoundValue value = this.getValue(rec);
        Set<AnnotationMirror> atm = value.getAnnotations();
        if (isClear) {
            UpperBoundValue val =
                    analysis.createSingleAnnotationValue(
                            UpperBoundAnnotatedTypeFactory.createAnnotation("Unknown", ""),
                            rec.getType());
            replace.put(rec, val);
        } else if (AnnotationUtils.containsSameByClass(atm, LTOMLengthOf.class)) {
            AnnotationMirror anno = AnnotationUtils.getAnnotationByClass(atm, LTOMLengthOf.class);
            AnnotationMirror newAnno =
                    UpperBoundAnnotatedTypeFactory.createAnnotation(
                            "LTLengthOf", UpperBoundUtils.getValue(anno));
            UpperBoundValue val =
                    analysis.createSingleAnnotationValue(
                            factory.getQualifierHierarchy().leastUpperBound(newAnno, anno),
                            rec.getType());
            replace.put(rec, val);
        } else if (AnnotationUtils.containsSameByClass(atm, LTLengthOf.class)) {
            AnnotationMirror anno = AnnotationUtils.getAnnotationByClass(atm, LTLengthOf.class);
            AnnotationMirror newAnno =
                    UpperBoundAnnotatedTypeFactory.createAnnotation(
                            "LTEqLengthOf", UpperBoundUtils.getValue(anno));
            UpperBoundValue val =
                    analysis.createSingleAnnotationValue(
                            factory.getQualifierHierarchy().leastUpperBound(newAnno, anno),
                            rec.getType());
            replace.put(rec, val);
        } else if (AnnotationUtils.containsSameByClass(atm, LTEqLengthOf.class)) {
            UpperBoundValue val =
                    analysis.createSingleAnnotationValue(
                            UpperBoundAnnotatedTypeFactory.createAnnotation("Unknown", ""),
                            rec.getType());
            replace.put(rec, val);
        }
    }

    private void applyAdd(
            Receiver rec,
            Map<Receiver, UpperBoundValue> replace,
            AnnotatedTypeFactory atypeFactory,
            Receiver caller) {

        UpperBoundAnnotatedTypeFactory factory = (UpperBoundAnnotatedTypeFactory) atypeFactory;
        UpperBoundValue value = this.getValue(rec);
        Set<AnnotationMirror> atm = value.getAnnotations();
        if (AnnotationUtils.containsSameByClass(atm, LTEqLengthOf.class)) {
            AnnotationMirror anno = AnnotationUtils.getAnnotationByClass(atm, LTEqLengthOf.class);
            String[] vals = UpperBoundUtils.getValue(anno);
            if (vals.length != 1 || !vals[0].equals(caller.toString())) {
                return;
            }
            AnnotationMirror newAnno =
                    UpperBoundAnnotatedTypeFactory.createAnnotation(
                            "LTLengthOf", caller.toString());
            UpperBoundValue val =
                    analysis.createSingleAnnotationValue(
                            factory.getQualifierHierarchy().greatestLowerBound(newAnno, anno),
                            rec.getType());
            replace.put(rec, val);
        } else if (AnnotationUtils.containsSameByClass(atm, LTLengthOf.class)) {
            AnnotationMirror anno = AnnotationUtils.getAnnotationByClass(atm, LTLengthOf.class);
            String[] vals = UpperBoundUtils.getValue(anno);
            if (vals.length != 1 || !vals[0].equals(caller.toString())) {
                return;
            }
            AnnotationMirror newAnno =
                    UpperBoundAnnotatedTypeFactory.createAnnotation(
                            "LTOMLengthOf", caller.toString());
            UpperBoundValue val =
                    analysis.createSingleAnnotationValue(
                            factory.getQualifierHierarchy().greatestLowerBound(newAnno, anno),
                            rec.getType());
            replace.put(rec, val);
        }
    }

    @Override
    public String toString() {
        String res = "";
        for (LocalVariable k : this.localVariableValues.keySet()) {
            UpperBoundValue anno = localVariableValues.get(k);
            res += k.toString() + ": " + anno.toString();
            res += "\n";
        }
        return res;
    }
}
