package com.mikesamuel.cil.ast.passes;

import javax.annotation.Nullable;

import com.mikesamuel.cil.ast.ArrayVisitor;
import com.mikesamuel.cil.ast.BaseArrayNode;
import com.mikesamuel.cil.ast.BaseClassNode;
import com.mikesamuel.cil.ast.BaseExpressionNode;
import com.mikesamuel.cil.ast.BaseInterfaceNode;
import com.mikesamuel.cil.ast.BaseLiteralNode;
import com.mikesamuel.cil.ast.BaseNameNode;
import com.mikesamuel.cil.ast.BaseNodeVisitor;
import com.mikesamuel.cil.ast.BasePackageNode;
import com.mikesamuel.cil.ast.BaseStatementNode;
import com.mikesamuel.cil.ast.BaseTemplateNode;
import com.mikesamuel.cil.ast.BaseTypeNode;
import com.mikesamuel.cil.ast.ClassVisitor;
import com.mikesamuel.cil.ast.ExpressionVisitor;
import com.mikesamuel.cil.ast.InterfaceVisitor;
import com.mikesamuel.cil.ast.LiteralVisitor;
import com.mikesamuel.cil.ast.NameVisitor;
import com.mikesamuel.cil.ast.PackageVisitor;
import com.mikesamuel.cil.ast.StatementVisitor;
import com.mikesamuel.cil.ast.TemplateVisitor;
import com.mikesamuel.cil.ast.TypeVisitor;

/**
 * Dispatches to delegate visitors.
 */
public abstract class DelegatingVisitor<T> extends BaseNodeVisitor<T> {
  private LiteralVisitor<T> literalVisitor = null;

  /** Sets the visitor that receives literal nodes. */
  public void setLiteralVisitor(LiteralVisitor<T> newLiteralVisitor) {
    this.literalVisitor = newLiteralVisitor;
  }

  @Override
  protected @Nullable T
  visitLiteral(@Nullable T x, BaseLiteralNode node) {
    if (literalVisitor != null) {
      return literalVisitor.visit(x, node);
    }
    return visitDefault(x, node);
  }

  private TypeVisitor<T> typeVisitor = null;

  /** Sets the visitor that receives type nodes. */
  public void setTypeVisitor(TypeVisitor<T> newTypeVisitor) {
    this.typeVisitor = newTypeVisitor;
  }

  @Override protected @Nullable T
  visitType(@Nullable T x, BaseTypeNode node) {
    if (typeVisitor != null) {
      return typeVisitor.visit(x, node);
    }
    return visitDefault(x, node);
  }

  private NameVisitor<T> nameVisitor = null;

  /** Sets the visitor that receives name nodes. */
  public void setNameVisitor(NameVisitor<T> newNameVisitor) {
    this.nameVisitor = newNameVisitor;
  }

  @Override protected @Nullable T
  visitName(@Nullable T x, BaseNameNode node) {
    if (nameVisitor != null) {
      return nameVisitor.visit(x, node);
    }
    return visitDefault(x, node);
  }

  private PackageVisitor<T> packageVisitor = null;

  /** Sets the visitor that receives package nodes. */
  public void setPackageVisitor(PackageVisitor<T> newPackageVisitor) {
    this.packageVisitor = newPackageVisitor;
  }

  @Override protected @Nullable T
  visitPackage(@Nullable T x, BasePackageNode node) {
    if (packageVisitor != null) {
      return packageVisitor.visit(x, node);
    }
    return visitDefault(x, node);
  }

  private ClassVisitor<T> classVisitor = null;

  /** Sets the visitor that receives class nodes. */
  public void setClassVisitor(ClassVisitor<T> newClassVisitor) {
    this.classVisitor = newClassVisitor;
  }

  @Override protected @Nullable T
  visitClass(@Nullable T x, BaseClassNode node) {
    if (classVisitor != null) {
      return classVisitor.visit(x, node);
    }
    return visitDefault(x, node);
  }

  private InterfaceVisitor<T> interfaceVisitor = null;

  /** Sets the visitor that receives interface nodes. */
  public void setInterfaceVisitor(InterfaceVisitor<T> newInterfaceVisitor) {
    this.interfaceVisitor = newInterfaceVisitor;
  }

  @Override protected @Nullable T
  visitInterface(@Nullable T x, BaseInterfaceNode node) {
    if (interfaceVisitor != null) {
      return interfaceVisitor.visit(x, node);
    }
    return visitDefault(x, node);
  }

  private ArrayVisitor<T> arrayVisitor = null;

  /** Sets the visitor that receives array nodes. */
  public void setArrayVisitor(ArrayVisitor<T> newArrayVisitor) {
    this.arrayVisitor = newArrayVisitor;
  }

  @Override protected @Nullable T
  visitArray(@Nullable T x, BaseArrayNode node) {
    if (arrayVisitor != null) {
      return arrayVisitor.visit(x, node);
    }
    return visitDefault(x, node);
  }

  private StatementVisitor<T> statementVisitor = null;

  /** Sets the visitor that receives statement nodes. */
  public void setStatementVisitor(StatementVisitor<T> newStatementVisitor) {
    this.statementVisitor = newStatementVisitor;
  }

  @Override protected @Nullable T
  visitStatement(@Nullable T x, BaseStatementNode node) {
    if (statementVisitor != null) {
      return statementVisitor.visit(x, node);
    }
    return visitDefault(x, node);
  }

  private ExpressionVisitor<T> expressionVisitor = null;

  /** Sets the visitor that receives expression nodes. */
  public void setExpressionVisitor(ExpressionVisitor<T> newExpressionVisitor) {
    this.expressionVisitor = newExpressionVisitor;
  }

  @Override protected @Nullable T
  visitExpression(@Nullable T x, BaseExpressionNode node) {
    if (expressionVisitor != null) {
      return expressionVisitor.visit(x, node);
    }
    return visitDefault(x, node);
  }

  private TemplateVisitor<T> templateVisitor = null;

  /** Sets the visitor that receives template nodes. */
  public void setTemplateVisitor(TemplateVisitor<T> newTemplateVisitor) {
    this.templateVisitor = newTemplateVisitor;
  }

  @Override protected @Nullable T
  visitTemplate(@Nullable T x, BaseTemplateNode node) {
    if (templateVisitor != null) {
      return templateVisitor.visit(x, node);
    }
    return visitDefault(x, node);
  }

}
