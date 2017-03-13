package org.checkerframework.checker.index.minlen2;

import java.util.LinkedHashSet;
import org.checkerframework.checker.index.lowerbound.LowerBoundChecker;
import org.checkerframework.checker.index.minlen.MinLenChecker;
import org.checkerframework.common.basetype.BaseTypeChecker;
import org.checkerframework.framework.source.SuppressWarningsKeys;

/**
 * An internal checker that collects information about the minimum lengths of arrays. This is a
 * special part of the MinLen Checker that depends on the Lower Bound Checker.
 *
 * @checker_framework.manual #index-checker Index Checker
 */
@SuppressWarningsKeys({"index", "minlen"})
public class MinLen2Checker extends BaseTypeChecker {

    @Override
    protected LinkedHashSet<Class<? extends BaseTypeChecker>> getImmediateSubcheckerClasses() {
        LinkedHashSet<Class<? extends BaseTypeChecker>> checkers =
                super.getImmediateSubcheckerClasses();
        checkers.add(LowerBoundChecker.class);
        checkers.add(MinLenChecker.class);
        return checkers;
    }
}
