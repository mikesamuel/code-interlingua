package foo;

import static com.mikesamuel.cil.HereBe._TEMPLATES_;

%%template fibArray(n) : Expression {
  new int[] { {%fibArrayElements(0, 1, 1, n) : VariableInitializerList} }
}

%%template fibArrayElements(a, b, i, n) : VariableInitializer {
  (% ((i <= n)
      ? cons(b, fibArrayElements(b, a + b, i + 1, n))
      : b))
}

class C {
  static final int[] fibs = (%fibArray(fibLength));
}
