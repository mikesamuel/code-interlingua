package com.mikesamuel.cil.ast.passes.flatten;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import com.mikesamuel.cil.ast.j8.ClassBodyNode;
import com.mikesamuel.cil.ast.j8.ClassMemberDeclarationNode;
import com.mikesamuel.cil.ast.j8.ConstantDeclarationNode;
import com.mikesamuel.cil.ast.j8.ExpressionAtomNode;
import com.mikesamuel.cil.ast.j8.FieldDeclarationNode;
import com.mikesamuel.cil.ast.j8.FieldNameNode;
import com.mikesamuel.cil.ast.j8.IdentifierNode;
import com.mikesamuel.cil.ast.j8.J8BaseNode;
import com.mikesamuel.cil.ast.j8.J8ExpressionNameReference;
import com.mikesamuel.cil.ast.j8.J8NodeType;
import com.mikesamuel.cil.ast.j8.J8TypeReference;
import com.mikesamuel.cil.ast.j8.LocalNameNode;
import com.mikesamuel.cil.ast.j8.MethodNameNode;
import com.mikesamuel.cil.ast.j8.ModifierNode;
import com.mikesamuel.cil.ast.j8.TypeNameNode;
import com.mikesamuel.cil.ast.j8.UnannTypeNode;
import com.mikesamuel.cil.ast.j8.UnqualifiedClassInstanceCreationExpressionNode;
import com.mikesamuel.cil.ast.j8.VariableDeclaratorIdNode;
import com.mikesamuel.cil.ast.j8.VariableDeclaratorListNode;
import com.mikesamuel.cil.ast.j8.VariableDeclaratorNode;
import com.mikesamuel.cil.ast.meta.FieldInfo;
import com.mikesamuel.cil.ast.meta.Name;
import com.mikesamuel.cil.ast.meta.StaticType;
import com.mikesamuel.cil.ast.meta.TypeInfo;
import com.mikesamuel.cil.ast.meta.TypeSpecification;
import com.mikesamuel.cil.ast.passes.TypeNodeFactory;
import com.mikesamuel.cil.ast.passes.flatten.PassState.ClosedOver;
import com.mikesamuel.cil.ast.passes.flatten.PassState.FlatteningType;
import com.mikesamuel.cil.util.LogUtils;

final class CaptureClosedOverStateMiniPass {
  final Logger logger;
  final StaticType.TypePool typePool;
  final TypeNodeFactory factory;

  CaptureClosedOverStateMiniPass(Logger logger, StaticType.TypePool typePool) {
    this.logger = logger;
    this.typePool = typePool;
    this.factory = new TypeNodeFactory(logger, typePool);
    this.factory.allowMethodContainers();
  }

  void run(PassState ps) {
    Multimap<BName, ClosedOver> closedOver = LinkedHashMultimap.create();
    Multimap<BName, BName> closedOverByReference =
        LinkedHashMultimap.create();
    for (PassState.FlatteningType ft : ps.inProcessOrder) {
      findClosedOverMembers(ps, ft, closedOver, closedOverByReference);
    }

    for (boolean repeat = true; repeat; repeat = false) {
      for (Map.Entry<BName, BName> e : closedOverByReference.entries()) {
        if (closedOver.putAll(e.getValue(), closedOver.get(e.getKey()))) {
          repeat = true;
        }
      }
    }

    for (Map.Entry<BName, Collection<ClosedOver>> e
         : closedOver.asMap().entrySet()) {
      PassState.FlatteningType ft = ps.byBumpyName.get(e.getKey());
      List<ClosedOver> closedOvers = new ArrayList<>(e.getValue());
      Collections.sort(closedOvers);
      ImmutableList.Builder<ClosedOver> inOrder = ImmutableList.builder();
      for (ClosedOver co : closedOvers) {
        if (!co.implicitlyClosesOver(ft)) {
          FieldInfo memberInfo = createFieldToStore(ft, co);
          ft.closedOverMembers.put(co, memberInfo);
          inOrder.add(co);
        }
      }
      ft.closedOverInOrder = inOrder.build();
    }
  }

  private void findClosedOverMembers(
      PassState ps,
      PassState.FlatteningType ft,
      Multimap<BName, ClosedOver> closedOver,
      Multimap<BName, BName> closedOverByReference) {

    // We look for all LocalName, FieldName, and MethodName fields.
    // We need all external locals referenced.

    // We need to take into account any that are found in field
    // initializers.

    // First we look outside field initializers.
    for (ExpressionAtomNode atom :
      ((J8BaseNode) ft.root)
      .finder(ExpressionAtomNode.class)
      .exclude(J8NodeType.FieldDeclaration, J8NodeType.ConstantDeclaration)
      .find()) {
      findClosedOverMembers(
          ps, ft.bumpyName, atom, closedOver, closedOverByReference);
    }

    // Now, go through the ones we missed in the first pass.
    for (Class<? extends J8BaseNode> declType
         : ImmutableList.of(
             FieldDeclarationNode.class, ConstantDeclarationNode.class)) {
      for (J8BaseNode decl : ((J8BaseNode) ft.root).finder(declType).find()) {
        for (ExpressionAtomNode atom
            : decl.finder(ExpressionAtomNode.class).find()) {
          if (findClosedOverMembers(
                  ps, ft.bumpyName, atom, closedOver, closedOverByReference)) {
            ft.eligibleForSimpleInitialization = false;
          }
        }
      }
    }
  }

  private boolean findClosedOverMembers(
      PassState ps, BName bname, ExpressionAtomNode atom,
      Multimap<BName, ClosedOver> closedOver,
      Multimap<BName, BName> closedOverByReference) {
    switch (atom.getVariant()) {
      // Must be captured if refers to outer class.
      case Local:
        return maybeCloseOver(
            atom.firstChildWithType(LocalNameNode.class),
            atom.getStaticType(),
            bname, ps, closedOver);
      case FreeField:
        return maybeCloseOver(
            atom.firstChildWithType(FieldNameNode.class),
            atom.getStaticType(),
            bname, ps, closedOver);
      case MethodInvocation:
        return maybeCloseOver(
            atom.firstChildWithType(MethodNameNode.class),
            atom.getStaticType(),
            bname, ps, closedOver);

      // Must be captured if the TypeName is explicit and does not name
      // the current type.
      case Super:
      case This:
        return maybeCloseOverThis(
            atom.firstChildWithType(TypeNameNode.class),
            bname, ps, closedOver);

      // We need to capture everything that is captured by the created
      // class.  For example, in
      //   void f(int start, int end) {
      //     return new Iterable<Integer>() {
      //       public Iterator<Integer> iterator() {
      //         return new Iterator<Integer>() {
      //           int i = start;
      //           public boolean hasNext() { return i < end; }
      //           public Integer next() { return i++; }
      //   };} };}
      // The inner Iterator captures (start, end) so the outer Iterable
      // must also capture them.
      case UnqualifiedClassInstanceCreationExpression: {
        UnqualifiedClassInstanceCreationExpressionNode cice =
            atom.firstChildWithType(
                UnqualifiedClassInstanceCreationExpressionNode.class);
        TypeInfo dti = cice != null ? cice.getDeclaredTypeInfo() : null;
        if (dti != null) {
          BName createdBName = BName.of(dti.canonName);
          if (ps.byBumpyName.containsKey(createdBName)) {
            closedOverByReference.put(createdBName, bname);
            return true;
          }
        }
        return false;
      }
      case ArrayConstructorReference:
      case ArrayCreationExpression:
      case ClassLiteral:
      case ConstructorReference:
      case Literal:
      case Parenthesized:
      case QuotedName:
      case StaticMember:
      case StaticReference:
        return false;
    }
    throw new AssertionError(atom);
  }

  private boolean maybeCloseOver(
      J8ExpressionNameReference nameRef, StaticType t, BName bname,
      PassState ps, Multimap<BName, ClosedOver> closedOver) {
    Name nm = nameRef != null ? nameRef.getReferencedExpressionName() : null;
    if (nm != null) {
      Name container = nm.getContainingClass();
      if (container.equals(bname.name)) { return false; } // Internal reference
      PassState.FlatteningType ft = ps.byBumpyName.get(BName.of(container));
      if (ft != null) {
        PassState.ClosedOver co = null;
        switch (nm.type) {
          case LOCAL:
            if (t != null) {
              co = new PassState.ClosedOverLocal(
                  t.typeSpecification, BName.of(nm));
            } else {
              LogUtils.log(
                  logger, Level.SEVERE, nameRef, "Missing type info", null);
            }
            break;
          case FIELD:
          case METHOD:
            co = new PassState.ClosedOverThisValue(ft);
            break;
          case AMBIGUOUS:
          case CLASS:
          case PACKAGE:
          case TYPE_PARAMETER:
            break;
        }
        if (co == null) {
          throw new AssertionError(nm);
        } else {
          closedOver.put(bname, co);
          return true;
        }
      }
    }
    return false;
  }

  private static boolean maybeCloseOverThis(
      J8TypeReference typeRef, BName bname, PassState ps,
      Multimap<BName, ClosedOver> closedOver) {
    TypeInfo ti = typeRef != null ? typeRef.getReferencedTypeInfo() : null;
    if (ti != null) {
      if (ti.canonName.equals(bname.name)) {
        // Internal reference
        return false;
      }
      PassState.FlatteningType ft = ps.byBumpyName.get(BName.of(ti.canonName));
      if (ft != null) {
        closedOver.put(bname, new PassState.ClosedOverThisValue(ft));
        return true;
      }
    }
    return false;
  }

  private FieldInfo createFieldToStore(FlatteningType ft, ClosedOver co) {
    TypeSpecification ts = co.getType(typePool.r);
    String fieldIdent = co.toFieldName(ft.nameAllocator);
    BName fieldName = BName.of(
        ft.bumpyName.name.child(fieldIdent, Name.Type.FIELD));
    StaticType t = typePool.type(ts, ft.root, logger);

    boolean useSimpleInit = ft.eligibleForSimpleInitialization;

    int modifiers = Modifier.PRIVATE;
    ImmutableList.Builder<J8BaseNode> b = ImmutableList.builder();
    b.add(ModifierNode.Variant.Private.buildNode());
    if (useSimpleInit) {
      b.add(ModifierNode.Variant.Final.buildNode());
      modifiers |= Modifier.FINAL;
    }
    b.add(UnannTypeNode.Variant.NotAtType.buildNode(factory.toTypeNode(t)));
    b.add(VariableDeclaratorListNode.Variant
        .VariableDeclaratorComVariableDeclarator.buildNode(
            VariableDeclaratorNode.Variant
            .VariableDeclaratorIdEqVariableInitializer.buildNode(
                VariableDeclaratorIdNode.Variant.IdentifierDims.buildNode(
                    IdentifierNode.Variant.Builtin.buildNode(
                        fieldName.name.identifier)))));

    FieldDeclarationNode decl = FieldDeclarationNode.Variant.Declaration
        .buildNode(b.build());
    ClassBodyNode classBody = ft.root.firstChildWithType(ClassBodyNode.class);
    classBody.add(
        ClassMemberDeclarationNode.Variant.FieldDeclaration.buildNode(
            decl));
    FieldInfo fi = new FieldInfo(modifiers, fieldName.name);
    fi.setValueType(ts);
    return fi;
  }
}
