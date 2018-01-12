package com.mikesamuel.cil.ast.passes.synth;

enum UseType {
  READ_OF_PRIVATE("get"),
  WRITE_OF_PRIVATE("set"),
  INVOKE_OF_PRIVATE("call"),
  SUPER_INVOKE("sup"),
  ;

  final String abbrev;
  UseType(String abbrev) {
    this.abbrev = abbrev;
  }
}