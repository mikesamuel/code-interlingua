package com.mikesamuel.cil.ptree;

import com.mikesamuel.cil.parser.ParSer;

abstract class PTParSer extends ParSer {
  enum Kind {
    ALT,
    CAT,
    REP,
    LIT,
    REX,
    REF,
    VNT,
  }

  abstract Kind getKind();
}
