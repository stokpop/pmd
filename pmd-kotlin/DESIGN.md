# pmd-kotlin — Design and Implementation Notes

This document captures design decisions, AST structure insights, and implementation
patterns for the `pmd-kotlin` module. It is a living document; update it as new
learnings accumulate.

---

## 1. Kotlin ANTLR AST Structure

Rules are written as XPath expressions evaluated against the Kotlin ANTLR AST.
The grammar lives in:
```
src/main/antlr4/net/sourceforge/pmd/lang/kotlin/ast/Kotlin.g4
```

All grammar rule names become AST node names in PascalCase
(e.g. `postfixUnaryExpression` → `PostfixUnaryExpression`).
Terminal tokens appear as `T-<TOKEN_NAME>` children (e.g. `T-THROW`, `T-RETURN`).

### 1.1 Method/property call chains

A chained expression like `a.b().c` is represented as a **left-recursive tree**
of `PostfixUnaryExpression` nodes, each adding one `PostfixUnarySuffix`:

```
PostfixUnaryExpression            ← a.b().c  (the whole thing)
  PostfixUnaryExpression          ← a.b()
    PostfixUnaryExpression        ← a.b
      PostfixUnaryExpression      ← a  (primary)
      PostfixUnarySuffix
        NavigationSuffix[@Identifier='b']
    PostfixUnarySuffix
      CallSuffix / ValueArguments  ← ()
  PostfixUnarySuffix
    NavigationSuffix[@Identifier='c']
```

**Key attributes:**
- `NavigationSuffix/@Identifier` — the method/property name accessed via `.`
- `CallSuffix` — present when the suffix is a call (has `ValueArguments`, possibly `AnnotatedLambda`)
- `ValueArguments/ValueArgument` — positional arguments of a call

**XPath patterns for chains:**
- Detect `.methodName` navigation: `PostfixUnarySuffix/NavigationSuffix[@Identifier='methodName']`
- Detect a call to `.methodName()`:
  `PostfixUnarySuffix/NavigationSuffix[@Identifier='methodName']`
  AND a sibling `PostfixUnarySuffix/CallSuffix` (at the parent's level)
- Detect `.trim().length` at the outer node:
  `PostfixUnarySuffix/NavigationSuffix[@Identifier='length']`
  AND `.//PostfixUnarySuffix/NavigationSuffix[@Identifier='trim']` in the subtree

### 1.2 Method arguments

```
PostfixUnaryExpression (the call)
  PostfixUnarySuffix
    CallSuffix
      ValueArguments
        ValueArgument
          ... expression ...       ← the argument
```

For lambda-form calls (`log.debug { ... }`):
```
PostfixUnaryExpression
  PostfixUnarySuffix
    CallSuffix
      AnnotatedLambda
        LambdaLiteral
          ...
```
The lambda form does NOT have `ValueArguments`.

### 1.3 String concatenation

`a + b` as part of an expression:
```
AdditiveExpression
  ...  (left operand)
  AdditiveOperator
    T-ADD
  ...  (right operand)
```
Token is `T-ADD` (not `T-PLUS`). Grammar: `additiveOperator : ADD | SUB`.

### 1.4 Try / catch / finally

```
TryExpression
  Block                          ← try body
  CatchBlock                     ← one per catch clause
    SimpleIdentifier             ← exception variable name (e.g. "e")
    Type                         ← exception type
    Block                        ← catch body
  FinallyBlock                   ← optional
    Block
```

**Empty catch detection:**
`Block[not(Statements/Statement)]` — `Block` always has `T-LCURL`, `T-RCURL` tokens as
children even when empty, so `Block[not(*)]` is WRONG. Use `Statements/Statement` instead.

`Statements` is always present as a grammar rule child of `Block`; `Statement` children
are only present when the block has actual statements.

**Throw/return in finally:**
```xpath
//FinallyBlock//JumpExpression[T-THROW]
except //FinallyBlock//LambdaLiteral//JumpExpression[T-THROW]
except //FinallyBlock//AnonymousFunction//JumpExpression[T-THROW]
```
Use `except` to exclude throws inside lambdas nested in the finally block
(those control their own exception flow). Both `LambdaLiteral` and `AnonymousFunction`
forms exist (`functionLiteral : lambdaLiteral | anonymousFunction`).

### 1.5 Jump expressions

`JumpExpression` wraps throw/return/continue/break:
```
JumpExpression
  T-THROW         ← for throw
  T-RETURN        ← for return
  T-CONTINUE      ← for continue
  T-BREAK         ← for break
```
Match with `JumpExpression[T-THROW]` etc.

### 1.6 When expressions

```
WhenExpression
  WhenSubject?                    ← the (expr) in  when (expr) { ... }
  WhenEntry*                      ← each branch incl. else
    WhenCondition / ELSE
    ControlStructureBody
```

`WhenEntry` includes the `else` branch. `count(WhenEntry)` counts all branches.

### 1.7 Loops

Grammar rule names → PMD AST node names:
- `forStatement`    → `ForStatement`
- `whileStatement`  → `WhileStatement`
- `doWhileStatement` → `DoWhileStatement`

Loop body is a `ControlStructureBody` child (which wraps a `Block` or single `Statement`).

Use the `ancestor::` axis to detect context inside a loop:
```xpath
ancestor::ForStatement or ancestor::WhileStatement or ancestor::DoWhileStatement
```

### 1.8 Assignments

```
Assignment
  directlyAssignableExpression / assignableExpression   ← left-hand side
  T-ASSIGNMENT  or  AssignmentAndOperator               ← = or +=, -=, etc.
    T-ADD_ASSIGNMENT   ← +=
    T-SUB_ASSIGNMENT   ← -=
    T-MULT_ASSIGNMENT  ← *=
    T-DIV_ASSIGNMENT   ← /=
    T-MOD_ASSIGNMENT   ← %=
  Expression                                            ← right-hand side
```

### 1.9 Identifier text access

Two patterns depending on node type:

| Node | Pattern |
|------|---------|
| `SimpleIdentifier` (standalone) | `SimpleIdentifier/T-Identifier/@Text` |
| Nodes with synthetic `@Identifier` attr | `NavigationSuffix/@Identifier` |

`NavigationSuffix`, `FunctionDeclaration` and similar nodes expose `@Identifier` as a
synthetic attribute (provided by `KotlinInnerNode.getIdentifier()`).

### 1.10 Empty block detection

`Block[not(Statements/Statement)]` — detects a block with no statements.
`Block[not(*)]` is INCORRECT because `Block` always has `T-LCURL`/`T-RCURL` tokens.

---

## 2. Custom XPath Functions

### 2.1 `pmd-kotlin:typeIs(fqcn)`

Returns `true` if the **type** of the context node is `fqcn` or a subtype.
Applies to nodes carrying type annotation from the kotlin-type-mapper pre-analysis:
- `PropertyDeclaration` — class-level property type
- `CatchBlock` — exception type
- `PostfixUnaryExpression` — expression type (limited coverage)

### 2.2 `pmd-kotlin:typeIsExactly(fqcn)`

Like `typeIs` but requires exact type match (no subtype).

### 2.3 `pmd-kotlin:matchesSig(sig)`

Returns `true` when the `PostfixUnaryExpression` context node **contains a call site**
matching the signature. Signature format: `receiverType#methodName(paramType,...)`.

**Range matching behaviour:**
- Single-line node: matches call sites within the column span.
- Multi-line node: matches call sites on lines where a direct `PostfixUnarySuffix` child
  starts. This avoids matching deeply-nested call sites inside lambda/block arguments.
- Block-like nodes with no direct `PostfixUnarySuffix` children (e.g. bare `try`, `when`)
  never match.

**Chained-call use case (e.g. `UseStringBuilderLength`):**
```xpath
//PostfixUnaryExpression[
    pmd-kotlin:matchesSig('java.lang.StringBuilder#toString()')
    and PostfixUnarySuffix/NavigationSuffix[@Identifier='length']
]
```
The outer node (`sb.toString().length`) has `toString()` as a call site in its range
AND has `.length` as a direct `NavigationSuffix`.

### 2.4 `pmd-kotlin:matchesSig` — wildcards

- `_` — wildcard for a single type (receiver or parameter)
- `*` — wildcard for the entire parameter list
- `<init>` — matches constructors

### 2.5 `pmd-kotlin:nodeText()`

Returns the source text of the node as a string.
Useful for simple text-based matching without type analysis.
Example: `pmd-kotlin:nodeText() = '0'` in `UseCollectionIsEmpty`.

### 2.6 `pmd-kotlin:hasAnnotation(fqcn)`

Returns `true` when the context node has a given annotation.

### 2.7 `pmd-kotlin:modifiers()`

Returns the set of modifier keywords on the context node
(e.g. `'open'`, `'abstract'`, `'private'`).

---

## 3. Rule Authoring Patterns

### 3.1 File / node layout for a new rule

1. **Rule entry** in the appropriate category XML:
   `src/main/resources/category/kotlin/{bestpractices,errorprone,performance,multithreading}.xml`

2. **Test data XML** (one per rule):
   `src/test/resources/net/sourceforge/pmd/lang/kotlin/rule/<category>/xml/<RuleName>.xml`

3. **Test class** (one per rule):
   `src/test/java/net/sourceforge/pmd/lang/kotlin/rule/<category>/<RuleName>Test.java`
   Extends `PmdRuleTst` — the framework auto-discovers the test XML by convention.

4. **Test method naming**: Checkstyle enforces `^[a-z][a-zA-Z0-9]*$`.
   Use camelCase even in test methods; underscores are not allowed.

### 3.2 Java-equivalent rule property

Rules ported from Java should include:
```xml
<property name="javaEquivalentRule" type="String"
          description="Java rule this Kotlin rule is based on; update this rule when the Java rule changes."
          value="category/java/performance.xml/SomeName"/>
```

### 3.3 Type-analysis requirement note

Rules using `typeIs`, `typeIsExactly`, or `matchesSig` should include in their
description:
```
Note: this rule requires kotlin-type-mapper pre-analysis to be available.
See the pmd-kotlin documentation for setup instructions.
```

### 3.4 Kotlin idiomatic alternatives

Always document the Kotlin-idiomatic fix in the rule description.  
Examples:
- `str.trim().length == 0` → `str.isBlank()`
- `sb.append("x" + y)` → `sb.append("x").append(y)` or `buildString { append("x"); append(y) }`
- `when` with 1–2 branches → `if`/`if-else`

### 3.5 `except` clause in XPath

PMD supports the `except` set-difference operator:
```xpath
//FinallyBlock//JumpExpression[T-RETURN]
except //FinallyBlock//AnonymousFunction//JumpExpression[T-RETURN]
```
Use it to exclude matched nodes that fall inside an inner scope
(lambda, anonymous function, nested try, etc.).

---

## 4. Import Ordering (Checkstyle)

PMD enforces strict lexicographic import grouping.
Correct order for `pmd-kotlin` source files:

```java
import java.*
import javax.*

import org.*

import net.sourceforge.pmd.*

import nl.stokpop.*   // comes AFTER net.sourceforge.pmd, not before
```

A blank line between non-adjacent groups is required, but extra blank lines
**within** a group are Checkstyle errors.

---

## 5. Aux Classpath Filtering

Kotlin's `FastJarHandler` / `LargeDynamicMappedBuffer` throws
`IllegalArgumentException` when given a `.pom` file (empty / non-ZIP).
Maven's Surefire and `URLClassLoader` hierarchies can include `.pom` entries.

**Fix location:** `KotlinLanguageProcessor.filterAuxClasspathEntries()` — retains
only entries that are existing directories or `.jar` files; logs `WARN` for anything
skipped. Applied at all three classpath-source points (string property, URLClassLoader,
`java.class.path`).

---

## 6. CPD / Duplication Avoidance

CPD flags structurally similar methods. Use the **template-method pattern** to
factor out shared call flow into an abstract base class:

- `AbstractKotlinTypeIsFunctionCall` — shared `call()` for `typeIs`/`typeIsExactly`
- Subclasses implement `matchesType()` hook (`isSubtypeOf` vs `isTypeEquivalent`)

Avoid dead private static methods that share a name with instance methods —
this was a source of false CPD positives.

---

## 7. `KotlinInnerNode.getIdentifier()`

Returns the text of the first `KtSimpleIdentifier` direct child.
Exposed as `@Identifier` attribute in XPath on any node that has a single leading
`simpleIdentifier` child (e.g. `NavigationSuffix`, `FunctionDeclaration`).

Not all nodes expose `@Identifier`; fall back to
`SimpleIdentifier/T-Identifier/@Text` for standalone `simpleIdentifier` children
(e.g. the exception variable in `CatchBlock`, function names in `FunctionNameTooShort`).
