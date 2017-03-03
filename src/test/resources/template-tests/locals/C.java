%%{
  let X = 1;

  package foo;

  import static com.mikesamuel.cil.HereBe._TEMPLATES_;

  class C {
    int x = (%X);

    %%{
      let Y = 2;
    %%}

    public static final int F = (%Y);  // Y above not in scope.

    C() {
      %%{
        let Y = X * 3;
        this.x = (%Y);
      %%}
    }
  }

%%}
