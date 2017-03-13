import org.checkerframework.checker.index.qual.*;

class MinLenOfNewArray {
    void test(int[] a) {
        int[] b = new int[a.length + 1];
        int k = b[0];
    }

    void test2(@Positive int a) {
        int @MinLen(1) [] b = new int[a];
    }
}
