package org.checkerframework.checker.index.minlen2;

import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.NewArrayTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreePath;
import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.lang.model.element.AnnotationMirror;
import org.checkerframework.checker.index.lowerbound.LowerBoundAnnotatedTypeFactory;
import org.checkerframework.checker.index.lowerbound.LowerBoundChecker;
import org.checkerframework.checker.index.minlen.MinLenAnnotatedTypeFactory;
import org.checkerframework.checker.index.minlen.MinLenChecker;
import org.checkerframework.checker.index.qual.MinLen;
import org.checkerframework.checker.index.qual.MinLenBottom;
import org.checkerframework.checker.index.qual.NonNegative;
import org.checkerframework.checker.index.qual.Positive;
import org.checkerframework.common.basetype.BaseAnnotatedTypeFactory;
import org.checkerframework.common.basetype.BaseTypeChecker;
import org.checkerframework.framework.qual.TypeUseLocation;
import org.checkerframework.framework.type.AnnotatedTypeMirror;
import org.checkerframework.framework.type.QualifierHierarchy;
import org.checkerframework.framework.type.treeannotator.ImplicitsTreeAnnotator;
import org.checkerframework.framework.type.treeannotator.ListTreeAnnotator;
import org.checkerframework.framework.type.treeannotator.PropagationTreeAnnotator;
import org.checkerframework.framework.type.treeannotator.TreeAnnotator;
import org.checkerframework.framework.util.AnnotationBuilder;
import org.checkerframework.framework.util.MultiGraphQualifierHierarchy;
import org.checkerframework.framework.util.defaults.QualifierDefaults;
import org.checkerframework.javacutil.AnnotationUtils;

/**
 * The MinLen checker is responsible for annotating arrays with their minimum lengths. This factory
 * implements only the part of the MinLen Checker's functionality that depends on the Lower Bound
 * Checker.
 */
public class MinLen2AnnotatedTypeFactory extends BaseAnnotatedTypeFactory {

    /** {@code @MinLen(0)}, which is the top qualifier. */
    final AnnotationMirror MIN_LEN_0;

    public MinLen2AnnotatedTypeFactory(BaseTypeChecker checker) {
        super(checker);
        AnnotationBuilder builder = new AnnotationBuilder(processingEnv, MinLen.class);
        builder.setValue("value", 0);
        MIN_LEN_0 = builder.build();
        this.postInit();
    }

    @Override
    protected void addCheckedCodeDefaults(QualifierDefaults defaults) {
        defaults.addCheckedCodeDefault(MIN_LEN_0, TypeUseLocation.OTHERWISE);
    }

    @Override
    protected Set<Class<? extends Annotation>> createSupportedTypeQualifiers() {
        return new LinkedHashSet<>(Arrays.asList(MinLen.class, MinLenBottom.class));
    }

    public LowerBoundAnnotatedTypeFactory getLowerBoundAnnotatedTypeFactory() {
        return getTypeFactoryOfSubchecker(LowerBoundChecker.class);
    }

    /** Returns the lowerbound type associated with the given ExpressionTree. */
    public AnnotatedTypeMirror lowerBoundTypeFromTree(Tree tree) {
        return getLowerBoundAnnotatedTypeFactory().getAnnotatedType(tree);
    }

    public MinLenAnnotatedTypeFactory getMinLenAnnotatedTypeFactory() {
        return getTypeFactoryOfSubchecker(MinLenChecker.class);
    }

    @Override
    public TreeAnnotator createTreeAnnotator() {
        return new ListTreeAnnotator(
                new MinLen2TreeAnnotator(this),
                new PropagationTreeAnnotator(this),
                new ImplicitsTreeAnnotator(this));
    }

    protected class MinLen2TreeAnnotator extends TreeAnnotator {

        public MinLen2TreeAnnotator(MinLen2AnnotatedTypeFactory factory) {
            super(factory);
        }

        @Override
        public Void defaultAction(Tree tree, AnnotatedTypeMirror type) {
            if (tree.getKind() != Tree.Kind.TYPE_PARAMETER && !isIntegerOperation(tree)) {
                AnnotationMirror anno =
                        getMinLenAnnotatedTypeFactory()
                                .getAnnotatedType(tree)
                                .getAnnotationInHierarchy(MIN_LEN_0);
                if (anno != null) {
                    type.replaceAnnotation(anno);
                }
            }
            return super.defaultAction(tree, type);
        }

        /**
         * For some reason getAnnotatedType fails in an internal call to LUB, claiming that the sets
         * passed to LUB have different sizes, if a unary operator is passed in the defaultAction
         * method above. Since this failure is undesirable, and those shouldn't have MinLen
         * annotations anyway, I'm avoiding the problem by checking ahead of time. Suzanne should be
         * made aware of this, though.
         */
        private boolean isIntegerOperation(Tree tree) {
            return tree.getKind() == Tree.Kind.PLUS
                    || tree.getKind() == Tree.Kind.MINUS
                    || tree.getKind() == Tree.Kind.MULTIPLY
                    || tree.getKind() == Tree.Kind.DIVIDE;
        }

        @Override
        public Void visitNewArray(NewArrayTree node, AnnotatedTypeMirror type) {
            if (node.getDimensions().size() > 0) {
                ExpressionTree dimExp = node.getDimensions().get(0);
                AnnotationMirror lbAnno =
                        lowerBoundTypeFromTree(dimExp)
                                .getAnnotationInHierarchy(
                                        getLowerBoundAnnotatedTypeFactory().UNKNOWN);
                if (AnnotationUtils.areSameByClass(lbAnno, Positive.class)) {
                    AnnotationMirror exisitingMinLen =
                            getMinLenAnnotatedTypeFactory()
                                    .getAnnotatedType(node)
                                    .getAnnotationInHierarchy(MIN_LEN_0);
                    AnnotationMirror newMinLen =
                            qualHierarchy.greatestLowerBound(createMinLen(1), exisitingMinLen);
                    type.replaceAnnotation(newMinLen == null ? createMinLen(1) : newMinLen);
                }
            }
            return super.visitNewArray(node, type);
        }
    }

    @Override
    public QualifierHierarchy createQualifierHierarchy(
            MultiGraphQualifierHierarchy.MultiGraphFactory factory) {
        return getMinLenAnnotatedTypeFactory().createQualifierHierarchy(factory);
    }

    public AnnotationMirror createMinLen(@NonNegative int val) {
        return getMinLenAnnotatedTypeFactory().createMinLen(val);
    }

    public int getMinLenFromString(String arrayExpression, Tree tree, TreePath currentPath) {
        return getMinLenAnnotatedTypeFactory()
                .getMinLenFromString(arrayExpression, tree, currentPath);
    }
}
