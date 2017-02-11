import org.checkerframework.checker.index.qual.*;

public class SubtractingNonNegatives {
    public static void m4(int[] a, @IndexFor("#1") int i, @IndexFor("#1") int j) {
        int k = i;
        // The checker correctly issues now warning on the if block below
        if (k >= j) {
            @IndexFor("a") int y = k;
        }
        for (k = i; k >= j; k -= j) {
            @IndexFor("a") int x = k; // index checker warning
        }
    }
}
