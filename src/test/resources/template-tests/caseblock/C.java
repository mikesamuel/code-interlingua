package foo;

import static com.mikesamuel.cil.HereBe._TEMPLATES_;

class C {
  C((%type) x) {
    switch (x) {
      %%for (oneCase : cases) {
        case (%oneCase.value):
          {% s for (s : oneCase.stmts) if (!oneCase.isDeprecated())}
          break;
      %%}
      %%if (defaultCase != null) {
        default:
          {{%s for (s : defaultCase.stmts)}}
      %%}
    }
  }
}
