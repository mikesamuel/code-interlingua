# code-interlingua

Defines a subset of Java that is useful as an intermediate
language for multi-backend code generators

## Intermediate Language

This project provides tools for generating code in a subset of
the Java 8 language and converting that into code in other
languages so that one tool can produce code that natively
runs in Java, C++, Python, JavaScript and that adding another
language to that list involves adapting a backend and writing
some supporting library.

The subset is derived from the
["Java Language Specification Chapter 19"](https://docs.oracle.com/javase/specs/jls/se8/html/jls-19.html)
grammar and excludes some kinds of statements that
do not have straight-forward equivalents in other
languages but which have alternatives within Java.


## Making code manipulation easy

We avoid common classes of errors in code generators by
passing around parse trees instead of strings of source code.

Manipulating parse trees can be hard though.  A parse tree
contains nodes that specification authors put there to work
around syntactic corner cases, and abstract syntax trees
don't require enough information to drive a serializer and
need to be separately documented.

For example, adding an `import foo.Bar;` is simple in Java,
but in the JLS grammar, there are a number of nodes involved.

```
ImportDeclaration
       |
       v
SingleTypeImport
       |
       v
    TypeName
       |
       v
      ...
```

*TypeName* can appear in both `import static foo.Bar.*` and `import foo.Bar`
so trying to create an *ImportDeclaration* from a *TypeName* is ambiguous
and requires quite detailed knowledge of the Java grammar.

*TypeName* is also lexically equivalent to a *PackageOrTypeName*.

This tool uses parse trees wherever possible but takes steps
to make them easy to use.

1. Robust templates allow generating well formed parse trees in
   an intuitive manner.
2. Intermediate node inference allows adding subtrees without
   creating all the intermediate nodes.
3. It includes stock passes that normalize trees.
   One manipulation involves replacing all unqualified names
   with fully-qualified ones, so that tools need not check
   two places (`import`s and `AmbiguousName`s) to figure
   out what is being referred to.

### Code templates

Having a structurally valid parse-tree whose nodes have clearly
defined semantics in the JLS is important to backend authors, but
code-generator writers who understand Java source shouldn't need
to read the JLS to use this tool.

A contextually escaped template system makes it easy to produce
fragments of parse trees using by interpolating sub-trees into a
larger Java source template and by using loops and conditionals to
repeat structures.

The Java Parser takes code like `x++` and converts it to a sequence of
parse events like

```
(PostExpr) (Primary) (ExprName) (Ident) ("x") (pop) (pop) (pop) ("++") (pop)
```

by generating a `(ProductionName)` for each production in the grammar entered
and a correspoding `(pop)` on exit.
This sequence of events is the flattened form of a parse tree:

```
(PostExpr)
    |
    +------(Primary)
    |       |
    |       +--------(ExprName)
    |                     |
    |                     +-----(Ident)
    |                              |
    |                              +----("x")
    |
    +-----------------------------------------------------------("++")
```

The parser can also run with a flag that allows extra nodes to be inserted.
The following syntax shows Java code interspersed with meta-instructions.

```
<% template HelloWorld : Statement %>
<% let id = autoname %>
for (int <% id %> = 0; i < limit; ++i) {
  <% action %>(buffer, i);
}
```

which might parse to a sequence of events like

```
(BlockStatements)
  (meta let id ...)
  (BasicForStatement)
    (ForInit)
      (LocalVariableDeclaration)
        (PrimitiveType) "int" (pop)
        (Identifier) (meta ...) (pop)
        ...
      (pop)
    (pop)
  (pop)
(pop)
```

During tree building, the meta events are interpreted to produce a valid parse
tree.  Meta-variables like the one in `<% action %>` resolve to parse trees that
are inserted into the tree using intermediate-node inference so that broad
node categories (statements, expressions, labels, and identifiers) can be
interpolated without worrying about details of the Java grammar.

The template language meta-syntax provides several kinds of operations:

1. Access to parse tree elements via free-variables like `action`.
2. Conditional and branching operations.
3. Call-outs to other templates.
4. Meta-variable declarations like `id` above
5. Conveniences like `autoname` above which makes it easy to avoid
   namespace conflicts.


## Translators for backend languages

TODO: Given a parse tree for the IL subset, produces code in the backend language.

TODO: Version 1: Java, C++, Python, JavaScript

The Java backend doesn't need to translate its input parse tree to a different language
so it just serializes the parse tree, compiles it to class files, and rewrites the
[*LineNumberTable*](http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html#jvms-4.7.12)
to point to the source positions attached to parse tree nodes.

### Translator requirements

Code that uses a translator must provide tables mapping

1. `instanceof` class targets to boolean function names that
   perform the check.
   This includes the `instanceof` implicit in
   `try { ... } catch (ExceptionType ex) { ... }`
   which is best handled by desugarring to
   ```java
   try {
     ...
   } finally {
     if (exceptionUnrollingCurrentThread instanceof ExceptionType) { ... }
   }`.
2. Static methods signatures used to function names.
3. Classes constructed to factory function names.
4. TODO


## Some stock parse-tree transformations

TODO: Given gnarly label based `break`/`continue`, produces a
logically equivalent tree that looks more like code a happy,
well-adjusted human might write.

## Java Features Excluded from the Subset

### Anonymous classes

Anonymous classes can be referred to explicitly by their JVM
name which is not stable across builds.

Translating code that uses anonymous classes complicates
name mangling in the translator, and there are always explicitly
named alternatives.


### Lambdas

There is a lot of auto-magic in lambdas, so translating
lambdas to languages like C++ is difficult and error-prone.


### Reflection

Reflective facilities vary greatly across languages
and simulating Java reflection would require generating
tables that make it hard to eliminate dead-code in languages
like JavaScript where compiled size matters.

As a result, all annotations are implicitly retention=SOURCE.


### `synchronized`

Not all languages provide locks -- JavaScript is event-loop
concurrent.  Languages that do provide locks often don't associate a
mutex with every object.

You can map `java.lang.concurrent` classes and methods in the
translator to map whatever subset of concurrency primitives you
require.

### Stack introspection

Stack introspection cannot be implemented effificently in other
languages since the kind of instrumentation done is born when an
exception is not thrown in tight loops, and probably cannot be done
when control passes out through ungenerated code without requiring a
second backend-specific instrumentation pass.

It's also almost always the wrong tool for any job.

### Exception subclassing

Different languages partition core failure modes into exceptions
differently.

```java
try {
  x = numerator() / denominator();
} catch (DivideByZeroException ex) {
  ...
} catch (ArithmeticException ex) {
  ...
}
```

In this case, any `ArithmeticExeception` thrown that is not a
division by zero will go to the second block.

But this snippet encodes lots of type-hierarchy assumptions that
are highly java-specific.

```java
try {
  ...
} catch (FileNotFoundException ex) {
  ...
} catch (IOException ex) {
  ...
}
```

does not involve core-JVM failure modes, so could be handled by
mapping IO exceptions to libraries that do use an exception-hierarchy
that mirrors that of Java.

Translators may assume optimistically that exception types have
no subclasses or super classes and that InternalError is never
thrown.

----

Some backend languages, like Go, do not provide fine-grained
exception dispatch because they have crafted the language to
encourage other failure-recovery idioms.

Others, like C++, often have libraries that are not exception-safe.
Propagating exceptions through unsafe code is a source of
hard-to-diagnose instability.

Backends may implicitly catch all exceptions that escape
translated code.

Robust generators should not assume that an escaping exception
doesn't just result in a process-level panic.

----

Checking instead of trapping certain classes of problems,
like `IOException`s, is a good way to run into
file-system race conditions.  The following is broken

```java
if (file.exists() && file.canRead()) {
  // file could be deleted or read privileges revoked before
  // the fopen can complete successfully.
  try (InputStream in = new FileInputStream(file)) {
    ...
  }
}
```

The best way to produce code that is robust on multiple backends
is to panic on exceptions, and move race-y operations like
IO operations into untranslated code and use function mapping
instead.

### Reference Equality for Primitive Wrappers

TODO (`java.lang.Integer` == `java.lang.Integer`)

A common pass inserts auto-boxing and unboxing instructions, so
`int`/`Integer` differences should not be a source of significant
bugs.

Non-null accepting collections like `ImmutableList<Integer>`
should be correctly mappable to unboxing collections like
`const vector<int>` (modulo generic unsoundness).




### Most of the core libraries.

It's easier to describe which parts of the core libraries are
supported without additional mapping in the translator than to
enumerate those which aren't.

Numeric primitives.
  Translators may fudge `long` precision, e.g. in JavaScript/OCAML.
  Translators may decline to overflow as well, e.g. Python bignums.

String -- See efficient text processing re character iteration.

StringBuilder/StringWriter/ByteArrayOutputStream -- See efficient
text processing.

Primitive arrays.

Basic collections like ArrayList, HashMap<String, ?>.



## Efficient text processing

Different backends have different native code-units.

1. Java & JavaScript treat strings as sequences of UTF-16.
2. Go provides excellent handling if all strings are represented
   as UTF-8.
3. Python stores strings as UTF-16 or UTF-32 internally depending
   on the flags the interpreter was compiled with.

It is possible in all these languages to efficiently process a
string in a left to right pass, but random-access into strings is
often only-efficient when the code-unit you want to access
(`charAt` vs `codePointAt`) is the same as the underlying
version.

For this reason, backends SHOULD make special effort to translate
the following left-to-right iteration idioms efficiently.

TODO

Appending output to the end of a buffer is efficient as is
truncation.  Random access and insertion into a buffer is
often not.



## Efficient Allocation

Java specifies minimal requirements for garbage collection, but
not all backends and clients will want to mandate garbage collection.

```java
try (ScopedInstance<T> t = new ScopedInstance<>(new T())) {

}
```

is an idiom that backends that want to be explicit about
deallocation should recognize.  That same type may be
extended to allow swap/move operations.



## Implied intermediate nodes

To solve problems like the `import` ambiguities solved above,
this project maintains a curated mapping of node type pairs
(container, descendant) to functions that create intermediate nodes so
that adding a *PackageOrTypeName* to an *ImportDeclaration* just works
and produces a complete and valid parse tree.

It also keeps a list of contexts in which we can infer content, like
empty `throws` and type parameter lists on method declarations.





## Caveats

### Dispatching to overloaded methods

Translating the precise semantics of

```java
void foo(String s) { ... }
void foo(CharSequence cs) { ... }

{
  String s = "";
  CharSequence cs = s;
  foo(s);
  foo(cs);
  foo((String) cs);
}
```

requires computing JVM method signatures for every call.
This was fairly straightforward prior to Java generics
but generic handling requires creating synthetic forwarding
methods, adding overloadings that don't appear in the source.

We make an effort to get this right, but expect bugs.

Translators that need to map calls to overloaded methods
to backend-specific variants MAY also find this a source
of maintenance headaches and translators MAY assume that
overloads are semantically equivalent and pick one.

This is problematic in some dispatching patterns:

```java

void foo(Object o) {
  if (o instanceof Number) { return foo((Number) o); }
  if (o instanceof Boolean) { return foo((Boolean) o); }
  ...
}
```

which might become infinitely recursive if overloading
is not handled.  Steer clear of this pattern.


### Exceptions

Some backend languages, like Go, do not provide fine-grained
exception dispatch because they have crafted the language to
encourage other failure-recovery idioms.

Others, like C++, often have libraries that are not exception-safe.
Propagating exceptions through unsafe code is a source of
hard-to-diagnose instability.

Backends may implicitly catch all exceptions that escape
translated code.

Robust generators should not assume that an escaping exception
doesn't just result in a process-level panic.
