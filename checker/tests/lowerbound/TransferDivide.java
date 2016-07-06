import org.checkerframework.checker.lowerbound.qual.*;

public class TransferDivide {

    void test() {
        int a = -1;
        int b = 0;
        int c = 1;
        int d = 2;

        /** literals */
        @Positive int e = -1 / -1;

        /** 0 / * -> NN */
        @NonNegative int f = 0 / a;
        @NonNegative int g = 0 / d;

        /** * / 1 -> * */
        @NegativeOnePlus int h = a / 1;
        @NonNegative int i = b / 1;
        @Positive int j = c / 1;
        @Positive int k = d / 1;

        /** pos / pos -> nn */
        @NonNegative int l = d / c;
        @NonNegative int m = c / d;
        //:: error: (assignment.type.incompatible)
        @Positive int n = c / d;

        /** nn / pos -> nn */
        @NonNegative int o = b / c;
        //:: error: (assignment.type.incompatible)
        @Positive int p = b / d;

        /** pos / nn -> nn */
        @NonNegative int q = d / l;
        //:: error: (assignment.type.incompatible)
        @Positive int r = c / l;

        /** nn / nn -> nn */
        @NonNegative int s = b / q;
	//:: error: (assignment.type.incompatible)
        @Positive int t = b / q;

        /** n1p / pos -> n1p */
        @NegativeOnePlus int u = a / d;
        @NegativeOnePlus int v = a / c;
        //:: error: (assignment.type.incompatible)
        @NonNegative int w = a / c;

        /** n1p / nn -> n1p */
        @NegativeOnePlus int x = a / l;
	//:: error: (assignment.type.incompatible)
        @NonNegative int y = a / l;
    }
}
