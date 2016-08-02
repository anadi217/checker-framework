package org.checkerframework.checker.upperbound;

import org.checkerframework.framework.flow.CFAbstractAnalysis;
import org.checkerframework.framework.flow.CFStore;
import org.checkerframework.framework.flow.CFValue;

import java.util.List;

import javax.lang.model.element.VariableElement;

import org.checkerframework.common.basetype.BaseTypeChecker;

import org.checkerframework.javacutil.Pair;

import org.checkerframework.framework.type.AnnotatedTypeMirror;

public class UpperBoundAnalysis extends CFAbstractAnalysis<CFValue, CFStore, UpperBoundTransfer> {
    public UpperBoundAnalysis(BaseTypeChecker checker, UpperBoundAnnotatedTypeFactory factory,
                              List<Pair<VariableElement, CFValue>> fieldValues) {
        super(checker, factory, fieldValues);
    }

    @Override
    public UpperBoundTransfer createTransferFunction() {
        return new UpperBoundTransfer(this);
    }

    @Override
    public CFValue createAbstractValue(AnnotatedTypeMirror type) {
        return new CFValue(this, type);
    }

    @Override
    public CFStore createCopiedStore(CFStore s) {
        return new CFStore(this, s);
    }

    @Override
    public CFStore createEmptyStore(boolean sequentialSemantics) {
        return new CFStore(this, sequentialSemantics);
    }
}