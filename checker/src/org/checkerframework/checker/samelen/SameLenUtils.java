package org.checkerframework.checker.samelen;

import javax.lang.model.element.AnnotationMirror;
import org.checkerframework.checker.upperbound.qual.*;
import org.checkerframework.javacutil.AnnotationUtils;

public class SameLenUtils {

    /**
     * Used to get the list of array names that an annotation applies to. Can return null if the
     * list would be empty.
     */
    public static String[] getValue(AnnotationMirror anno) {
        if (!AnnotationUtils.hasElementValue(anno, "value")) {
            return null;
        }
        return AnnotationUtils.getElementValueArray(anno, "value", String.class, true)
                .toArray(new String[0]);
    }
}
