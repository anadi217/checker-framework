import org.checkerframework.checker.index.qual.GTENegativeOne;
import org.checkerframework.checker.index.qual.IndexOrHigh;
import org.checkerframework.checker.index.qual.IndexOrLow;
import org.checkerframework.checker.index.qual.LTLengthOf;

public class IndexOrLowTests {
    int[] array = {1, 2};

    @IndexOrLow("array")
    int index = -1;

    void test() {
        //:: error: (array.access.unsafe.low)
        array[index] = 1;

        if (index > -1) {
            array[index] = 1;
        }

        @IndexOrHigh("array") int y = index + 1;
        if (y < array.length) {
            array[y] = 1;
        }
        //:: error: (assignment.type.incompatible)
        index = -4;
    }

    void test2(@LTLengthOf("array") @GTENegativeOne int param) {
        index = array.length - 1;
        @LTLengthOf("array") @GTENegativeOne int x = index;
        index = param;
    }

    void test3(int[] a, int b) {
        @IndexOrLow("a")
        int k;
        //:: error: (assignment.type.incompatible)
        k = a.length - b;
    }
}
