import java.util.*;
import org.checkerframework.checker.minlen.qual.*;

class ListSupport {

    void newListMinLen() {
        List<Integer> list = new ArrayList<Integer>();

        //:: error: (assignment.type.incompatible)
        @MinLen(1) List<Integer> list2 = list;

        @MinLen(0) List<Integer> list3 = list;
    }
    
    void listGet(@MinLen(10) List<Integer> list) {
        list.get(5);
        
        //:: error: (argument.type.incompatible)
        list.get(20);
    }
    
    void listRemove(@MinLen(10) List<Integer> lst) {
        List<Integer> list = lst;
        list.remove(0);
        
        //:: error: (assignment.type.imcompatible)
        @MinLen(10) List<Integer> list2 = list;
        
        @MinLen(9) List<Integer> list3  = list;
    }
    
    void listRemoveAliasing(@MinLen(10) List<Integer> lst) {
        List<Integer> list = lst;
        @MinLen(10) List<Integer> list2 = list;

        list2.remove(0);
        
        //:: error: (assignment.type.imcompatible)
        @MinLen(10) List<Integer> list3 = list;
        
        @MinLen(9) List<Integer> list4  = list;
    }
    
    void listAdd(@MinLen(10) List<Integer> lst) {
        List<Integer> list = lst;
        list.add(0);
       
        @MinLen(11) List<Integer> list2 = list;
    }
    
    void listClear(@MinLen(10) List<Integer> lst) {
        List<Integer> list = lst;
        list.clear();
        
        //:: error: (assignment.type.imcompatible)
        @MinLen(1) List<Integer> list2 = list;
        
        @MinLen(0) List<Integer> list3  = list;
    }
}
