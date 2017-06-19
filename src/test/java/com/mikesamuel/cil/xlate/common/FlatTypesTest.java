package com.mikesamuel.cil.xlate.common;

import java.util.logging.Logger;

import org.junit.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.mikesamuel.cil.ast.j8.J8BaseNode;
import com.mikesamuel.cil.ast.j8.J8FileNode;
import com.mikesamuel.cil.ast.j8.J8TypeDeclaration;
import com.mikesamuel.cil.ast.meta.Name;
import com.mikesamuel.cil.ast.meta.TypeInfo;
import com.mikesamuel.cil.ast.passes.CommonPassRunner;
import com.mikesamuel.cil.ast.passes.PassTestHelpers;
import com.mikesamuel.cil.ast.passes.PassTestHelpers.LoggableOperation;
import com.mikesamuel.cil.xlate.common.FlatTypes.FlatParamInfo;

import junit.framework.TestCase;

@SuppressWarnings("javadoc")
public final class FlatTypesTest extends TestCase {

  static ImmutableSet<Name> getDeclaredTypes(
      Iterable<? extends J8FileNode> files) {
    ImmutableSet.Builder<Name> b = ImmutableSet.builder();
    for (J8FileNode fn : files) {
      for (J8TypeDeclaration d :
           ((J8BaseNode) fn).finder(J8TypeDeclaration.class).find()) {
        TypeInfo ti = d.getDeclaredTypeInfo();
        if (ti != null) {
          b.add(ti.canonName);
        }
      }
    }
    return b.build();
  }

  private static FlatTypes flatTypesForSource(
      String[][] inpLines, String... expectedErrors) {
    return PassTestHelpers.expectErrors(
        new LoggableOperation<FlatTypes>() {

          @Override
          public FlatTypes run(Logger logger) {
            ImmutableList<J8FileNode> cus =
                PassTestHelpers.parseCompilationUnits(inpLines);
            CommonPassRunner runner = new CommonPassRunner(logger);
            cus = runner.run(cus);
            FlatTypes fts = new FlatTypes(logger, runner.getTypeInfoResolver());
            ImmutableSet<Name> namesDeclared = getDeclaredTypes(cus);
            // TODO: shuffle
            for (Name nameDeclared : namesDeclared) {
              fts.recordType(BName.of(nameDeclared));
            }
            fts.disambiguate();
            return fts;
          }

        }, expectedErrors);
  }


  @Test
  public static void testAmbiguousTypeParameters() {
    FlatTypes fts = flatTypesForSource(
        new String[][] {
          {
            "package com.example;",
            "class C<A> {",
            "  class D<A, B> {}",
            "}",
          },
        });

    Name comExample = Name.DEFAULT_PACKAGE.child("com", Name.Type.PACKAGE)
        .child("example", Name.Type.PACKAGE);
    Name c = comExample.child("C", Name.Type.CLASS);
    Name c_a = c.child("A", Name.Type.TYPE_PARAMETER);
    Name cd = c.child("D", Name.Type.CLASS);
    Name cd_a = cd.child("A", Name.Type.TYPE_PARAMETER);
    Name cd_b = cd.child("B", Name.Type.TYPE_PARAMETER);

    Name cdFlat = comExample.child("C$D", Name.Type.CLASS);

    assertEquals(FName.of(c), fts.getFlatTypeName(BName.of(c)));
    assertEquals(FName.of(cdFlat), fts.getFlatTypeName(BName.of(cd)));
    ImmutableList<Name> params = ImmutableList.of(c_a, cd_a, cd_b);
    ImmutableList.Builder<Name> errored = ImmutableList.builder();
    for (Name param : params) {
      try {
        fts.getFlatTypeName(BName.of(param));
        continue;
      } catch (@SuppressWarnings("unused") IllegalArgumentException e) {
        errored.add(param);
      }
    }
    assertEquals(params, errored.build());

    // flat C has one type parameter
    assertEquals(
        ImmutableMap.of(BName.of(c_a), FName.of(c_a)),
        fts.getFlatParamInfo(BName.of(c)).substMap);
    assertEquals(
        ImmutableList.of(BName.of(c_a)),
        fts.getFlatParamInfo(BName.of(c)).bumpyParametersInOrder);
    assertEquals(
        ImmutableList.of(FName.of(c_a)),
        fts.getFlatParamInfo(BName.of(c)).flatParametersInOrder);

    // C.D has two type parameters and one inherited one
    assertEquals(
        ImmutableList.of(
            BName.of(c_a),
            BName.of(cd_a),
            BName.of(cd_b)),
        fts.getFlatParamInfo(BName.of(cd)).bumpyParametersInOrder);
    assertEquals(
        ImmutableList.of(
            FName.of(cdFlat.child("A_0", Name.Type.TYPE_PARAMETER)),
            FName.of(cdFlat.child("A", Name.Type.TYPE_PARAMETER)),
            FName.of(cdFlat.child("B", Name.Type.TYPE_PARAMETER))),
        fts.getFlatParamInfo(BName.of(cd)).flatParametersInOrder);
    assertEquals(
        ImmutableMap.of(
            BName.of(c_a), FName.of(cdFlat.child("A_0", Name.Type.TYPE_PARAMETER)),
            BName.of(cd_a), FName.of(cdFlat.child("A", Name.Type.TYPE_PARAMETER)),
            BName.of(cd_b), FName.of(cdFlat.child("B", Name.Type.TYPE_PARAMETER))),
        fts.getFlatParamInfo(BName.of(cd)).substMap);
  }

  @Test
  public static void testAmbiguousInnerClasses() {
    FlatTypes fts = flatTypesForSource(
        new String[][] {
          {
            "class C {",
            "  class D {}",
            "}",
          },
          {
            "class C$D {}",
          }
        });

    assertEquals(
        "/C",
        fts.getFlatTypeName(
            BName.of(Name.DEFAULT_PACKAGE.child("C", Name.Type.CLASS)))
        .toString());
    assertEquals(
        "/C\\$D_1",
        fts
            .getFlatTypeName(
                BName.of(
                    Name.DEFAULT_PACKAGE.child("C", Name.Type.CLASS)
                    .child("D", Name.Type.CLASS)))
            .toString());
    assertEquals(
        "/C\\$D",
        fts.getFlatTypeName(
            BName.of(Name.DEFAULT_PACKAGE.child("C$D", Name.Type.CLASS)))
        .toString());
  }

  @Test
  public static void testAmbiguousInnerClassesFirstPublic() {
    FlatTypes fts = flatTypesForSource(
        new String[][] {
          {
            "class C {",
            "  public class D {}",
            "}",
          },
          {
            "class C$D {}",
          }
        });

    assertEquals(
        "/C",
        fts.getFlatTypeName(
            BName.of(Name.DEFAULT_PACKAGE.child("C", Name.Type.CLASS)))
        .toString());
    assertEquals(
        "/C\\$D",
        fts
            .getFlatTypeName(
                BName.of(
                    Name.DEFAULT_PACKAGE.child("C", Name.Type.CLASS)
                    .child("D", Name.Type.CLASS)))
            .toString());
    assertEquals(
        "/C\\$D_1",
        fts.getFlatTypeName(
            BName.of(Name.DEFAULT_PACKAGE.child("C$D", Name.Type.CLASS)))
        .toString());
  }

  @Test
  public static void testAmbiguousInnerClassesSecondPublic() {
    FlatTypes fts = flatTypesForSource(
        new String[][] {
          {
            "class C {",
            "  class D {}",
            "}",
          },
          {
            "public class C$D {}",
          }
        });

    assertEquals(
        "/C",
        fts.getFlatTypeName(
            BName.of(Name.DEFAULT_PACKAGE.child("C", Name.Type.CLASS)))
        .toString());
    assertEquals(
        "/C\\$D_1",
        fts
            .getFlatTypeName(
                BName.of(
                    Name.DEFAULT_PACKAGE.child("C", Name.Type.CLASS)
                    .child("D", Name.Type.CLASS)))
            .toString());
    assertEquals(
        "/C\\$D",
        fts.getFlatTypeName(
            BName.of(Name.DEFAULT_PACKAGE.child("C$D", Name.Type.CLASS)))
        .toString());
  }

  @Test
  public static void testAmbiguousInnerClassesCannotBeResolvedPeacefully() {
    FlatTypes fts = flatTypesForSource(
        new String[][] {
          {
            "class C {",
            "  public class D {}",
            "}",
          },
          {
            "public class C$D {}",
          }
        },
        "Cannot flatten public type /C$D to /C\\$D since that would conflict"
        + " with /C\\$D"
        );

    assertEquals(
        "/C",
        fts.getFlatTypeName(
            BName.of(Name.DEFAULT_PACKAGE.child("C", Name.Type.CLASS)))
        .toString());
    assertEquals(
        "/C\\$D_1",
        fts
            .getFlatTypeName(
                BName.of(
                    Name.DEFAULT_PACKAGE.child("C", Name.Type.CLASS)
                    .child("D", Name.Type.CLASS)))
            .toString());
    assertEquals(
        "/C\\$D",
        fts.getFlatTypeName(
            BName.of(Name.DEFAULT_PACKAGE.child("C$D", Name.Type.CLASS)))
        .toString());
  }

  public static void testMaskingInMethodScope() {
    FlatTypes fts = flatTypesForSource(
        new String[][] {
          {
            "//test",
            "import java.util.ArrayList;",
            "import java.util.List;",
            "class C<X> {",
            "  class D<X> {",
            "    class E {",
            "      <X_0 extends X>",
            "      List<? super X_0> f() {",
            "        return new ArrayList<X>();",
            "      }",
            "    }",
            "  }",
            "}",
          },
        });

    Name c = Name.DEFAULT_PACKAGE.child("C", Name.Type.CLASS);
    Name cd = c.child("D", Name.Type.CLASS);
    Name cde = cd.child("E", Name.Type.CLASS);
    Name f = cde.method("f", 1);

    Name cdeFlat = Name.DEFAULT_PACKAGE.child("C$D$E", Name.Type.CLASS);
    Name fFlat = cdeFlat.method("f", 1);

    FlatParamInfo cdeInfo = fts.getFlatParamInfo(BName.of(cde));
    assertEquals(
        ImmutableList.of(
            BName.of(c.child("X", Name.Type.TYPE_PARAMETER)),
            BName.of(cd.child("X", Name.Type.TYPE_PARAMETER))),
        cdeInfo.bumpyParametersInOrder);
    assertEquals(
        ImmutableList.of(
            // Not X_0 since that is used by f().
            FName.of(cdeFlat.child("X_1", Name.Type.TYPE_PARAMETER)),
            FName.of(cdeFlat.child("X", Name.Type.TYPE_PARAMETER))),
        cdeInfo.flatParametersInOrder);

    FlatParamInfo fInfo = fts.getFlatParamInfo(BName.of(f));
    assertEquals(
        ImmutableList.of(
            BName.of(f.child("X_0", Name.Type.TYPE_PARAMETER))),
        fInfo.bumpyParametersInOrder);
    assertEquals(
        ImmutableList.of(
            FName.of(fFlat.child("X_0", Name.Type.TYPE_PARAMETER))),
        fInfo.flatParametersInOrder);
    assertEquals(
        ImmutableMap.of(
            BName.of(f.child("X_0", Name.Type.TYPE_PARAMETER)),
            FName.of(fFlat.child("X_0", Name.Type.TYPE_PARAMETER))),
        fInfo.substMap);
  }

}
