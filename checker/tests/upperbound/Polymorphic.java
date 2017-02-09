import org.checkerframework.checker.index.qual.*;

class Polymorphic {

    //Identity functions

    int @PolyMinLen [] minlen_identity(int @PolyMinLen [] a) {
        return a;
    }

    int @PolySameLen [] samelen_identity(int @PolySameLen [] a) {
        int @SameLen("a") [] x = a;
        return a;
    }

    @PolyUpperBound
    int ubc_identity(@PolyUpperBound int a) {
        return a;
    }

    // MinLen tests
    void minlen_id(int @MinLen(5) [] a) {
        int @MinLen(5) [] b = minlen_identity(a);
        //:: error: (assignment.type.incompatible)
        int @MinLen(6) [] c = minlen_identity(b);
    }

    // SameLen tests
    void samelen_id(int @SameLen("#2") [] a, int[] a2) {
        int[] banana;
        int @SameLen("a2") [] b = samelen_identity(a);
        //:: error: (assignment.type.incompatible)
        int @SameLen("banana") [] c = samelen_identity(b);
    }

    // UpperBound tests
    void ubc_id(
            int[] a,
            int[] b,
            @LTLengthOf("#1") int ai,
            @LTEqLengthOf("#1") int al,
            @LTLengthOf({"#1", "#2"}) int abi,
            @LTEqLengthOf({"#1", "#2"}) int abl) {
        int[] c;

        @LTLengthOf("a") int ai1 = ubc_identity(ai);
        //:: error: (assignment.type.incompatible)
        @LTLengthOf("b") int ai2 = ubc_identity(ai);

        @LTEqLengthOf("a") int al1 = ubc_identity(al);
        //:: error: (assignment.type.incompatible)
        @LTLengthOf("a") int al2 = ubc_identity(al);

        @LTLengthOf({"a", "b"}) int abi1 = ubc_identity(abi);
        //:: error: (assignment.type.incompatible)
        @LTLengthOf({"a", "b", "c"}) int abi2 = ubc_identity(abi);

        @LTEqLengthOf({"a", "b"}) int abl1 = ubc_identity(abl);
        //:: error: (assignment.type.incompatible)
        @LTEqLengthOf({"a", "b", "c"}) int abl2 = ubc_identity(abl);
    }
}
