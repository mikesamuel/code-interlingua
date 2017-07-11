package com.mikesamuel.cil.expr;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;

import javax.annotation.Nullable;

import org.junit.Test;

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.Lists;
import com.google.common.io.Files;
import com.google.common.io.Resources;
import com.mikesamuel.cil.ast.Trees;
import com.mikesamuel.cil.ast.j8.ClassBodyNode;
import com.mikesamuel.cil.ast.j8.CompilationUnitNode;
import com.mikesamuel.cil.ast.j8.ExpressionNode;
import com.mikesamuel.cil.ast.j8.FieldDeclarationNode;
import com.mikesamuel.cil.ast.j8.IdentifierNode;
import com.mikesamuel.cil.ast.j8.J8BaseNode;
import com.mikesamuel.cil.ast.j8.J8NodeType;
import com.mikesamuel.cil.ast.j8.J8TypeDeclaration;
import com.mikesamuel.cil.ast.j8.MethodBodyNode;
import com.mikesamuel.cil.ast.j8.MethodDeclarationNode;
import com.mikesamuel.cil.ast.j8.NormalClassDeclarationNode;
import com.mikesamuel.cil.ast.j8.StatementNode;
import com.mikesamuel.cil.ast.j8.UnannTypeNode;
import com.mikesamuel.cil.ast.j8.VariableDeclaratorIdNode;
import com.mikesamuel.cil.ast.j8.VariableDeclaratorNode;
import com.mikesamuel.cil.ast.j8.VariableInitializerNode;
import com.mikesamuel.cil.ast.meta.FieldInfo;
import com.mikesamuel.cil.ast.meta.JavaLang;
import com.mikesamuel.cil.ast.meta.Name;
import com.mikesamuel.cil.ast.meta.StaticType;
import com.mikesamuel.cil.ast.meta.StaticType.TypePool;
import com.mikesamuel.cil.ast.meta.StaticType.TypePool.ClassOrInterfaceType;
import com.mikesamuel.cil.ast.meta.TypeInfo;
import com.mikesamuel.cil.ast.meta.TypeInfoResolver;
import com.mikesamuel.cil.ast.meta.TypeSpecification;
import com.mikesamuel.cil.ast.passes.CommonPassRunner;
import com.mikesamuel.cil.parser.Input;
import com.mikesamuel.cil.parser.LeftRecursion;
import com.mikesamuel.cil.parser.ParseErrorReceiver;
import com.mikesamuel.cil.parser.ParseResult;
import com.mikesamuel.cil.parser.ParseState;
import com.mikesamuel.cil.parser.SList;
import com.mikesamuel.cil.parser.SourcePosition;
import com.mikesamuel.cil.ptree.PTree;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import junit.framework.TestCase;

@SuppressWarnings("javadoc")
public final class InterpreterTest extends TestCase {

  // TODO: Make this run log clean.

  static final class InterpreterTestContext {
    final J8BaseNode root;
    final Logger logger;
    final ClassLoader loader;
    final TypePool typePool;

    InterpreterTestContext(
        J8BaseNode root, Logger logger,
        ClassLoader loader, TypePool typePool) {
      this.root = root;
      this.logger = logger;
      this.loader = loader;
      this.typePool = typePool;
    }
  }

  private InterpreterTestContext contextFor(
      J8NodeType nodeType, String code, String source) {
    Logger logger = Logger.getAnonymousLogger();

    Input inp = Input.builder().code(code).source(source).build();
    ParseResult result = PTree.complete(nodeType).getParSer()
        .parse(new ParseState(inp), new LeftRecursion(),
               ParseErrorReceiver.DEV_NULL);
    assertEquals(
        inp.content().toString(),
        ParseResult.Synopsis.SUCCESS, result.synopsis);
    ParseState afterParse = result.next();
    J8BaseNode root = Trees
        .forGrammar(J8NodeType.CompilationUnit.getGrammar())
        .of(inp, afterParse.output);

    ClassLoader loader = getClass().getClassLoader();
    if (loader == null) { loader = ClassLoader.getSystemClassLoader(); }

    CommonPassRunner passes = new CommonPassRunner(logger);
    passes.setTypeInfoResolver(
        TypeInfoResolver.Resolvers.forClassLoader(loader));
    switch (root.getNodeType()) {
      case Expression:
        root = passes.run((ExpressionNode) root);
        break;
      case CompilationUnit:
      case TemplatePseudoRoot:
        root = (J8BaseNode) passes.run((CompilationUnitNode) root);
        break;
      case Statement:
        root = passes.run((StatementNode) root);
        break;
      default:
    }

    TypeInfoResolver typeInfoResolver = passes.getTypeInfoResolver();
    TypePool typePool = passes.getTypePool();
    if (typePool == null) {
      typePool = new TypePool(typeInfoResolver);
    }

    return new InterpreterTestContext(root, logger, loader, typePool);
  }

  private void assertExprResult(
      Completion<Object> want, String expr, Object... typesIdentsAndValues) {
    assertResult(want, J8NodeType.Expression, expr, typesIdentsAndValues);
  }

  private void assertResult(
      Completion<Object> want, J8NodeType nodeType, String code,
      Object... typesIdentsAndValues) {
    InterpreterTestContext tc = contextFor(nodeType, code, getName());
    InterpretationContext<Object> ctx = new InterpretationContextImpl(
        tc.logger, tc.loader, tc.typePool);

    J8BaseNode root = tc.root;

    Locals<Object> locals = new Locals<>();
    for (int i = 0, n = typesIdentsAndValues.length; i < n; i += 3) {
      TypeSpecification typ = (TypeSpecification) typesIdentsAndValues[i];
      Name name = Name.root(
          (String) typesIdentsAndValues[i + 1], Name.Type.AMBIGUOUS);
      Object initialValue = typesIdentsAndValues[i + 2];
      locals.declare(
          name,
          ctx.coercion(ctx.getTypePool().type(typ, null, ctx.getLogger())));
      locals.set(name, initialValue);
    }

    Interpreter<Object> interpreter = new Interpreter<>(ctx);
    Object got = interpreter.interpret(root, locals);

    assertEquals(want, got);
  }

  @Test
  public void testLiterals() {
    assertExprResult(Completion.normal(1), "1");
    assertExprResult(Completion.normal(10), "0xA");
    assertExprResult(Completion.normal(12), "014");
    assertExprResult(Completion.normal(1L), "1L");
    assertExprResult(Completion.normal(1f), "1f");
    assertExprResult(Completion.normal(0.5D), "0.5D");
    assertExprResult(Completion.normal(0.5), "0.5");
    assertExprResult(Completion.normal(0.5e2), "0.5e2");
    assertExprResult(Completion.normal(true), "true");
    assertExprResult(Completion.normal(false), "false");
    assertExprResult(Completion.normal(null), "null");
    assertExprResult(Completion.normal("foo\bar"), "\"foo\\bar\"");
    assertExprResult(Completion.normal("foo"), "\"foo\"");
    assertExprResult(Completion.normal(""), "\"\"");
    assertExprResult(Completion.normal('c'), "'c'");
    assertExprResult(Completion.normal('\0'), "'\\0'");
  }

  @Test
  public void testUnaryOperators() {
    assertExprResult(Completion.normal(-1), "-1");
    assertExprResult(Completion.normal(1), "+1");
    assertExprResult(Completion.normal(-1L), "-1L");
    assertExprResult(Completion.normal(true), "!false");
    assertExprResult(Completion.normal(false), "!true");
    assertExprResult(Completion.normal(~0x1234), "~0x1234");
  }

  @Test
  public void testCasts() {
    assertExprResult(Completion.normal((byte) 0), "(byte) 0");
    assertExprResult(Completion.normal(42.0), "(double) 42");
    assertExprResult(Completion.normal(""), "(String) \"\"");
    assertExprResult(Completion.normal(null), "(String) null");
  }

  @Test
  public void testLocalReads() {
    assertExprResult(
        Completion.normal(123),
        "i",
        StaticType.T_INT.typeSpecification, "i", 123);
    assertExprResult(
        Completion.normal(3),
        "arr.length",
        StaticType.T_INT.typeSpecification.arrayOf(), "arr", new int[3]);
  }

  @Test
  public void testArrayAccess() {
    assertExprResult(
        Completion.normal(0D),
        "arr[1]",
        StaticType.T_DOUBLE.typeSpecification.arrayOf(), "arr", new double[3]);
    assertExprResult(
        Completion.normal(1D),
        "arr[0] = 1",
        StaticType.T_DOUBLE.typeSpecification.arrayOf(), "arr", new double[3]);
    assertExprResult(
        Completion.normal(2D),
        "(arr[0] = 1) + arr[0]",
        StaticType.T_DOUBLE.typeSpecification.arrayOf(), "arr", new double[3]);
  }

  private String pathTo(String pathSuffix) throws IOException {
    try (BufferedReader r = Resources.asCharSource(
        getClass().getResource("/all-sources.txt"),
        Charsets.UTF_8).openBufferedStream()) {
      for (String line; (line = r.readLine()) != null;) {
        if (line.endsWith(pathSuffix)) {
          return line;
        }
      }
    }
    fail("No source available for ..." + pathSuffix);
    return null;
  }

  @Test
  public void testTestExpressions() throws Exception {
    String path = pathTo("/expr/TestExpressions.java");

    InterpreterTestContext tc = contextFor(
        J8NodeType.CompilationUnit,
        Files.toString(new File(path), Charsets.UTF_8),
        TestExpressions.class.getSimpleName() + ".java");
    Locals<Object> locals = new Locals<>();

    Name thisTypeName;
    {
      String[] nameParts = TestExpressions.class.getName().split("[.]");
      Name nm = Name.DEFAULT_PACKAGE;
      for (int i = 0, n = nameParts.length; i < n; ++i) {
        nm = nm.child(
            nameParts[i], i + 1 == n ? Name.Type.CLASS : Name.Type.PACKAGE);
      }
      thisTypeName = nm;
    }

    ClassOrInterfaceType thisType = (ClassOrInterfaceType) tc.typePool.type(
        TypeSpecification.unparameterized(thisTypeName), null, tc.logger);

    InterpretationContext<Object> ctx = new InterpretationContextImpl(
        tc.logger, tc.loader, tc.typePool) {

      {
        this.setThisType(thisType.info);
      }

      Optional<Name> toLocalName(Name fieldName) {
        if (thisTypeName.equals(fieldName.getContainingClass())) {
          return Optional.of(fieldName.parent.child(
              fieldName.identifier, Name.Type.LOCAL));
        }
        return Optional.absent();
      }

      @Override
      public Object setField(
          FieldInfo field, Object container, Object newValue) {
        Optional<Name> localName = toLocalName(field.canonName);
        if (localName.isPresent()) {
          return locals.set(localName.get(), newValue);
        } else {
          return super.setField(field, container, newValue);
        }
      }

      @Override
      public Object setStaticField(FieldInfo field, Object newValue) {
        Optional<Name> localName = toLocalName(field.canonName);
        if (localName.isPresent()) {
          return locals.set(localName.get(), newValue);
        } else {
          return super.setStaticField(field, newValue);
        }
      }

      @Override
      public Object getField(FieldInfo field, Object container) {
        Optional<Name> localName = toLocalName(field.canonName);
        if (localName.isPresent()) {
          return locals.get(localName.get(), errorValue());
        } else {
          return super.getField(field, container);
        }
      }

      @Override
      public Object getStaticField(FieldInfo field) {
        Optional<Name> localName = toLocalName(field.canonName);
        if (localName.isPresent()) {
          return locals.get(localName.get(), errorValue());
        } else {
          return super.getStaticField(field);
        }
      }
    };

    Interpreter<Object> interpreter = new Interpreter<>(ctx);

    Optional<NormalClassDeclarationNode> testExpressionsClassOpt =
        tc.root
        .finder(NormalClassDeclarationNode.class)
        .match(
            new Predicate<NormalClassDeclarationNode>() {

              @Override
              public boolean apply(NormalClassDeclarationNode p) {
                TypeInfo ti = p.getDeclaredTypeInfo();
                return ti != null
                    && ti.canonName.identifier.equals(
                        TestExpressions.class.getSimpleName());
              }

            })
        .findOne();

    assertTrue(testExpressionsClassOpt.isPresent());

    ClassBodyNode testExpressionsClassBody = testExpressionsClassOpt
        .get()
        .firstChildWithType(ClassBodyNode.class);

    List<Runnable> valueTests = Lists.newArrayList();
    for (FieldDeclarationNode decl
        : testExpressionsClassBody
            .finder(FieldDeclarationNode.class)
            .exclude(J8TypeDeclaration.class)
            .find()) {
      UnannTypeNode typeNode = decl.firstChildWithType(UnannTypeNode.class);
      StaticType type = typeNode.getStaticType();
      for (VariableDeclaratorNode declarator
          : decl.finder(VariableDeclaratorNode.class)
                .exclude(VariableInitializerNode.class)
                .find()) {
        VariableDeclaratorIdNode id = declarator.firstChildWithType(
            VariableDeclaratorIdNode.class);
        IdentifierNode name = id.firstChildWithType(IdentifierNode.class);
        VariableInitializerNode init = declarator.firstChildWithType(
            VariableInitializerNode.class);
        assertNotNull(name);

        Name localName = thisTypeName.child(name.getValue(), Name.Type.LOCAL);
        locals.declare(localName, ctx.coercion(type));

        if (init != null) {
          Completion<Object> completion = interpreter.interpret(init, locals);
          assertEquals(Completion.Kind.NORMAL, completion.kind);
          Object value = completion.value;
          if (ctx.isErrorValue(value)) {
            fail("Failed to compute result for " + localName);
          }
          locals.set(localName, value);

          valueTests.add(new Runnable() {

              @Override
              // fail calls trigger inside test runner since value tests are
              // run synchronously below.
              @SuppressFBWarnings("IJU_ASSERT_METHOD_INVOKED_FROM_RUN_METHOD")
              public void run() {
                Field f;
                try {
                  f = TestExpressions.class.getDeclaredField(
                      localName.identifier);
                } catch (NoSuchFieldException | SecurityException e) {
                  e.printStackTrace();
                  fail("Failed to access " + localName.identifier);
                  return;
                }
                Object expected;
                try {
                  expected = f.get(null);
                } catch (IllegalArgumentException | IllegalAccessException e) {
                  e.printStackTrace();
                  fail("Failed to access " + localName.identifier);
                  return;
                }
                Object got = locals.get(
                    localName,
                    new Object() {
                      @Override public String toString() {
                        return "[Missing local declaration]";
                      }
                    });
                requireDeepEquals(
                    localName.identifier, init.getSourcePosition(), null,
                    expected, got);
              }

            });
        }
      }
    }
    for (Runnable valueTest : valueTests) {
      valueTest.run();
    }
  }

  static void requireDeepEquals(
      String message, SourcePosition sourcePosition,
      @Nullable SList<Integer> path,
      @Nullable Object expected, @Nullable Object value) {
    boolean same;
    if (expected == value) {
      same = true;
    } else if (expected == null || value == null) {
      same = false;
    } else if (expected.getClass() != value.getClass()) {
      same = false;
    } else {
      Class<?> cl = expected.getClass();
      if (cl.isArray()) {
        int n = Array.getLength(expected);
        if (n != Array.getLength(value)) {
          same = false;
        } else {
          for (int i = 0; i < n; ++i) {
            requireDeepEquals(
                message, sourcePosition, SList.append(path, i),
                Array.get(expected, i), Array.get(value, i));
          }
          same = true;
        }
      } else if (expected instanceof Iterable<?>) {
        Iterator<?> a = ((Iterable<?>) expected).iterator();
        Iterator<?> b = ((Iterable<?>) value).iterator();
        for (int i = 0; a.hasNext() && b.hasNext(); ++i) {
          requireDeepEquals(
              message, sourcePosition, SList.append(path, i),
              a.next(), b.next());
        }
        same = !(a.hasNext() || b.hasNext());
      } else if (expected instanceof Double) {
        double a = (Double) expected;
        double b = (Double) value;
        // TODO: ignore insig digits.
        same = (a == b || ((a != a) && (b != b)));
      } else if (expected instanceof Float) {
        double a = (Float) expected;
        double b = (Float) value;
        // TODO: ignore insig digits.
        same = (a == b || ((a != a) && (b != b)));
      } else {
        same = expected.equals(value);
      }
    }
    if (!same) {
      fail(
          sourcePosition + ": " + message + ": expected (" + expected
          + ") but got (" + value + ") at " + SList.forwardIterable(path));
    }
  }

  public void testTestStatements() throws Exception {
    String path = pathTo("/expr/TestStatements.java");

    InterpreterTestContext tc = contextFor(
        J8NodeType.CompilationUnit,
        Files.toString(new File(path), Charsets.UTF_8),
        TestStatements.class.getSimpleName() + ".java");
    Locals<Object> locals = new Locals<>();

    Name thisTypeName;
    {
      String[] nameParts = TestStatements.class.getName().split("[.]");
      Name nm = Name.DEFAULT_PACKAGE;
      for (int i = 0, n = nameParts.length; i < n; ++i) {
        nm = nm.child(
            nameParts[i], i + 1 == n ? Name.Type.CLASS : Name.Type.PACKAGE);
      }
      thisTypeName = nm;
    }

    ClassOrInterfaceType thisType = (ClassOrInterfaceType) tc.typePool.type(
        TypeSpecification.unparameterized(thisTypeName), null, tc.logger);

    InterpretationContext<Object> ctx = new InterpretationContextImpl(
        tc.logger, tc.loader, tc.typePool);
    ctx.setThisType(thisType.info);

    Interpreter<Object> interpreter = new Interpreter<>(ctx);

    // Find the f(String) method.
    MethodDeclarationNode decl = tc.root
        .finder(MethodDeclarationNode.class)
        .exclude(J8NodeType.MethodBody)
        .find()
        .get(0);
    MethodBodyNode body = decl.firstChildWithType(MethodBodyNode.class);
    Preconditions.checkNotNull(body);

    Name methodName = decl.getMemberInfo().canonName;
    assertEquals("f", methodName.identifier);
    Name paramName = methodName.child("s", Name.Type.LOCAL);

    // Set up parameters
    String testInputString = "inputToF";
    locals.declare(paramName, ctx.coercion(ctx.getTypePool().type(
        JavaLang.JAVA_LANG_STRING, null, ctx.getLogger())));
    locals.set(paramName, ctx.from(testInputString));

    // Interpret the body.
    Completion<Object> result = interpreter.interpret(body, locals);

    // Returns are translated to normal results when traversing the method body.
    assertEquals(Completion.Kind.RETURN, result.kind);

    List<String> want = TestStatements.f(testInputString);
    assertEquals(want.getClass(), result.value.getClass());
    Iterable<?> got = (Iterable<?>) result.value;

    assertEquals(Joiner.on('\n').join(want), Joiner.on('\n').join(got));
  }

}
