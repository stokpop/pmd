# Kotlin Type Analysis — `pmd-kotlin` Custom XPath Functions

`pmd-kotlin` provides custom XPath functions that let rules check the **resolved types,
call signatures, annotations, and modifiers** of Kotlin code, analogous to the functions
available in `pmd-java`.

---

## Functions

### `pmd-kotlin:typeIs(typeName)`

Returns `true` when the context node is a declaration whose resolved type matches `typeName`.

| Context node | Checked field |
|---|---|
| `PropertyDeclaration` | declared property / local variable type |
| `FunctionDeclaration` | return type |
| `FunctionValueParameter` | function / constructor parameter type |
| `CatchBlock` | caught exception type |
| `ForStatement` | loop variable type |

```xpath
//PropertyDeclaration[pmd-kotlin:typeIs('java.text.DecimalFormat')]
//FunctionDeclaration[pmd-kotlin:typeIs('java.util.Calendar')]
//FunctionValueParameter[pmd-kotlin:typeIs('java.util.Map')]
//CatchBlock[pmd-kotlin:typeIs('java.io.IOException')]
//ForStatement[pmd-kotlin:typeIs('kotlin.String')]
```

### `pmd-kotlin:matchesSig(sig)`

Returns `true` when the context node is an expression that is a call site matching the given
signature pattern.

```xpath
//PostfixUnaryExpression[pmd-kotlin:matchesSig('java.util.regex.Pattern#compile(_)')]
```

### `pmd-kotlin:hasAnnotation(className)`

Returns `true` when the context declaration node carries an annotation whose name matches
`className`. Accepts both simple names and fully-qualified names. Applicable on
`ClassDeclaration`, `FunctionDeclaration`, `PropertyDeclaration`, and `ObjectDeclaration`.

```xpath
//ClassDeclaration[pmd-kotlin:hasAnnotation('Service')]
//ClassDeclaration[pmd-kotlin:hasAnnotation('org.springframework.stereotype.Service')]
//FunctionDeclaration[pmd-kotlin:hasAnnotation('Deprecated')]
//PropertyDeclaration[pmd-kotlin:hasAnnotation('javax.persistence.Column')]
```

Name matching strategy (checked in order):
1. `@TypeName` on `UnescapedAnnotation` child nodes — set by kotlin-type-mapper when the
   annotation class is on the aux classpath and the FQN can be resolved.
2. `ANNOTATION_NAMES_KEY` comma list stored on the declaration node.
3. Source-text fallback — reads the annotation name as written in source. Always works
   for simple names regardless of whether type resolution ran.

> **Note:** FQN queries (e.g. `hasAnnotation('org.springframework.stereotype.Service')`)
> require the annotation's JAR on the **auxiliary classpath** so kotlin-type-mapper can
> resolve the FQN. Simple-name queries always work without any classpath.

### `pmd-kotlin:modifiers()`

Returns the explicit modifier keywords of the context declaration node as a string sequence.
No arguments. Applicable on `ClassDeclaration`, `FunctionDeclaration`, `PropertyDeclaration`,
`ObjectDeclaration`, and similar.

Supported modifier values:

| Category | Values |
|---|---|
| Visibility | `public`, `private`, `protected`, `internal` |
| Inheritance | `abstract`, `final`, `open` |
| Class | `data`, `sealed`, `enum`, `inner`, `value`, `annotation` |
| Member | `override`, `lateinit` |
| Function | `suspend`, `inline`, `infix`, `operator`, `tailrec`, `external` |
| Property | `const` |
| Parameter | `vararg`, `noinline`, `crossinline` |
| Platform | `expect`, `actual` |

```xpath
//FunctionDeclaration[pmd-kotlin:modifiers() = 'suspend']
//ClassDeclaration[pmd-kotlin:modifiers() = 'data']
//PropertyDeclaration[pmd-kotlin:modifiers() = ('const', 'internal')]
//FunctionDeclaration[pmd-kotlin:modifiers() = ('override', 'suspend')]
```

---

## Type Name Formats

`typeIs` and `matchesSig` accept **Java FQCNs and Kotlin FQNs interchangeably** for well-known types.
The mapping is defined in `TypeNameUtils.kt` in `kotlin-type-mapper:model`.

Examples of equivalent pairs:

| Java FQCN | Kotlin FQN |
|---|---|
| `java.lang.String` | `kotlin.String` |
| `java.lang.Object` | `kotlin.Any` |
| `java.util.List` | `kotlin.collections.List` |
| `java.util.Map` | `kotlin.collections.Map` |
| `java.lang.Iterable` | `kotlin.collections.Iterable` |

Non-mapped types (e.g. `java.util.Calendar`, `java.util.regex.Pattern`) are matched exactly.

---

## Signature Format (`matchesSig`)

```
[receiverType "#"] methodName "(" [paramList] ")"
```

| Part | Value | Meaning |
|---|---|---|
| `receiverType` | FQN or `_` | Omit or use `_` to match any receiver |
| `methodName` | identifier or `_` | `_` matches any name |
| `paramList` | comma-separated types | Use `_` per-param wildcard, `*` for any count |

```
java.util.regex.Pattern#matches(java.lang.String,java.lang.CharSequence)
java.util.regex.Pattern#compile(_)           -- one param, any type
_#trim()                                     -- trim() on any receiver
kotlin.collections.List#size()               -- property access on List
_#_(*) 	                                     -- any call with any params
```

**Java↔Kotlin equivalence applies to receiver and parameter types.** So
`java.util.regex.Pattern#matches(java.lang.String,java.lang.CharSequence)` also matches a call
whose argument types are `kotlin.String` and `kotlin.CharSequence`.

**Static Java methods** (no dispatch receiver, e.g. `Pattern.matches(...)`) are matched by
the class prefix of the fully-qualified callee name.

---

## Auxiliary Classpath

`typeIs`, `matchesSig`, and FQN-based `hasAnnotation` queries require kotlin-type-mapper to
resolve external type references. Pass JAR files via the `auxClasspath` language property
(same property as `pmd-java`):

```xml
<!-- In pmd configuration -->
<language name="kotlin" version="1.8">
  <property name="auxClasspath" value="/path/to/spring-context.jar:/path/to/app.jar"/>
</language>
```

In the **PMD Designer**, use the _Auxiliary classpath_ panel to add JARs. They are
automatically forwarded to kotlin-type-mapper.

Without any classpath, simple-name `hasAnnotation` queries and `modifiers()` still work
because they read the source text directly.

---

## Pre-Analysis Requirement

`typeIs` and `matchesSig` rely on a `KotlinTypeAnalysisContext` produced by
[kotlin-type-mapper](https://github.com/stokpop/kotlin-type-mapper).

`KotlinLanguageProcessor` runs kotlin-type-mapper automatically on the files being
analyzed — no manual setup is required in production. In the PMD Designer (single-file mode),
analysis runs inline on each snippet.

### In tests

Use `KotlinTypeXPathTestHelper` to run the analyzer and inject the context:

```java
@BeforeEach
void setUp() {
    helper = KotlinTypeXPathTestHelper.forDirectory(new File(resourceDir));
    helper.injectContext();   // runs kotlin-type-mapper, sets global context
}

@AfterEach
void tearDown() {
    KotlinTypeAnalysisContextHolder.clearGlobal();
}
```

---

## Example Rules

### Avoid `DecimalFormat` as a field (`errorprone.xml`)

```xml
<rule name="AvoidDecimalFormatAsField"
      language="kotlin"
      message="DecimalFormat is not thread-safe; avoid storing it as a field."
      class="net.sourceforge.pmd.lang.rule.xpath.XPathRule">
  <description>...</description>
  <properties>
    <property name="xpath">
      <value><![CDATA[
        //PropertyDeclaration[
            not(ancestor::FunctionBody)
            and (pmd-kotlin:typeIs('java.text.DecimalFormat')
              or pmd-kotlin:typeIs('java.text.ChoiceFormat')
              or pmd-kotlin:typeIs('java.text.NumberFormat'))
        ]
      ]]></value>
    </property>
  </properties>
</rule>
```

### Avoid recompiling `Pattern` inside a function (`bestpractices.xml`)

```xml
<rule name="AvoidRecompilingPatterns"
      language="kotlin"
      message="Avoid compiling Pattern inside a function; use a companion object or top-level val."
      class="net.sourceforge.pmd.lang.rule.xpath.XPathRule">
  <description>...</description>
  <properties>
    <property name="xpath">
      <value><![CDATA[
        //FunctionBody//PostfixUnaryExpression[
            pmd-kotlin:matchesSig('java.util.regex.Pattern#compile(_)')
        ]
      ]]></value>
    </property>
  </properties>
</rule>
```

### Flag non-`open` `@Service` classes (Spring)

```xml
<rule name="SpringServiceShouldBeOpen"
      language="kotlin"
      message="Spring @Service classes should be open (or use the kotlin-spring plugin) to allow proxying."
      class="net.sourceforge.pmd.lang.rule.xpath.XPathRule">
  <description>...</description>
  <properties>
    <property name="xpath">
      <value><![CDATA[
        //ClassDeclaration[
            pmd-kotlin:hasAnnotation('org.springframework.stereotype.Service')
            and not(pmd-kotlin:modifiers() = 'open')
        ]
      ]]></value>
    </property>
  </properties>
</rule>
```

### Detect non-suspended `@Transactional` functions

```xml
<rule name="TransactionalFunctionShouldBeSuspendable"
      language="kotlin"
      message="@Transactional on a non-suspend function may not work correctly in coroutine contexts."
      class="net.sourceforge.pmd.lang.rule.xpath.XPathRule">
  <description>...</description>
  <properties>
    <property name="xpath">
      <value><![CDATA[
        //FunctionDeclaration[
            pmd-kotlin:hasAnnotation('Transactional')
            and not(pmd-kotlin:modifiers() = 'suspend')
        ]
      ]]></value>
    </property>
  </properties>
</rule>
```

---

## Known Limitations

- `typeIs` and `matchesSig` require pre-computed type analysis via kotlin-type-mapper.
  Without it, they return `false` for all nodes (safe default, no false positives).
- `hasAnnotation` with a FQN requires the annotation's JAR on the aux classpath.
  Simple-name queries always work without any classpath.
- `modifiers()` reads explicit (written) modifiers only — no implicit modifiers are inferred
  (e.g. `final` is not returned for a class that has no `open` modifier).
- Analysis is based on kotlin-type-mapper's K1 PSI; line numbers may differ by ±1 from PMD's
  ANTLR parser for some declarations. A ±1 fallback is applied automatically.
- Generic type arguments are supported in signatures (e.g. `kotlin.collections.List<kotlin.String>`),
  but omitting the type argument matches the raw (erased) type.

---

## Design Notes

### Type resolution: K1 compiler, not import heuristics

All type names exposed on PMD nodes (`@TypeName`, `@ReturnTypeName`) and used by `typeIs` /
`typeIsExactly` are **resolved FQNs produced by the Kotlin K1 compiler** inside kotlin-type-mapper,
not guessed from import statements. This means:

- A `PropertyDeclaration` node whose `@TypeName` is set carries the real FQN as the compiler
  sees it — e.g. `nl.stokpop.kotlin.Simple`, not the bare source name `Simple`.
- If `@TypeName` is absent, it means analysis did not produce a result for that node
  (e.g. a compilation error, or a node type not yet handled). The XPath functions return
  `false` in this case — **no false positives, but potentially false negatives**.
- Import-based fallback guessing was deliberately **not** implemented. Guessing FQNs from
  imports can produce false positives when multiple classes share a simple name, and it
  would be misleading to show a guessed type in the PMD Designer while `typeIs` on the same
  node still returns `false`. The K1 compiler already handles all normal import resolution
  correctly; if a type is missing it points to a gap in the analyzer, not a need for heuristics.

### `*` prefix convention (vs Java)

In the Java module the PMD Designer shows `Type: *SomeClass` when Java's type system has an
**unresolved** symbol for a node — the class was referenced but not found on the aux classpath.
Kotlin does not use this convention: if kotlin-type-mapper could not resolve a type, the
`@TypeName` attribute is simply absent rather than present with a `*` marker. The effect is
the same (the Designer shows nothing for unresolved types) but the representation differs.

### `typeIs` vs `typeIsExactly`

Mirrors the split in `pmd-java`:

| Function | Matches |
|---|---|
| `pmd-kotlin:typeIs('X')` | X and all subtypes (BFS over `typeHierarchy`, requires compiled classes on auxClasspath) |
| `pmd-kotlin:typeIsExactly('X')` | X only, no hierarchy walk |

When the aux classpath is not set, `typeIs` falls back to exact matching because the
`typeHierarchy` map is empty — behaviour is identical to `typeIsExactly` in that case.


`pmd-kotlin` provides two custom XPath functions that let rules check the **resolved types and
call signatures** of Kotlin code, analogous to `pmd-java:typeIs` and `pmd-java:matchesSig` in
the Java module.

---

## Functions

### `pmd-kotlin:typeIs(typeName)`

Returns `true` when the context node is a declaration whose resolved type matches `typeName`.

| Context node | Checked field |
|---|---|
| `PropertyDeclaration` | declared property type |
| `FunctionDeclaration` | return type |

```xpath
//PropertyDeclaration[pmd-kotlin:typeIs('java.text.DecimalFormat')]
//FunctionDeclaration[pmd-kotlin:typeIs('java.util.Calendar')]
```

### `pmd-kotlin:matchesSig(sig)`

Returns `true` when the context node is an expression that is a call site matching the given
signature pattern.

```xpath
//PostfixUnaryExpression[pmd-kotlin:matchesSig('java.util.regex.Pattern#compile(_)')]
```

---

## Type Name Formats

Both functions accept **Java FQCNs and Kotlin FQNs interchangeably** for well-known types.
The mapping is defined in `TypeNameUtils.kt` in `kotlin-type-mapper:model`.

Examples of equivalent pairs:

| Java FQCN | Kotlin FQN |
|---|---|
| `java.lang.String` | `kotlin.String` |
| `java.lang.Object` | `kotlin.Any` |
| `java.util.List` | `kotlin.collections.List` |
| `java.util.Map` | `kotlin.collections.Map` |
| `java.lang.Iterable` | `kotlin.collections.Iterable` |

Non-mapped types (e.g. `java.util.Calendar`, `java.util.regex.Pattern`) are matched exactly.

---

## Signature Format (`matchesSig`)

```
[receiverType "#"] methodName "(" [paramList] ")"
```

| Part | Value | Meaning |
|---|---|---|
| `receiverType` | FQN or `_` | Omit or use `_` to match any receiver |
| `methodName` | identifier or `_` | `_` matches any name |
| `paramList` | comma-separated types | Use `_` per-param wildcard, `*` for any count |

```
java.util.regex.Pattern#matches(java.lang.String,java.lang.CharSequence)
java.util.regex.Pattern#compile(_)           -- one param, any type
_#trim()                                     -- trim() on any receiver
kotlin.collections.List#size()               -- property access on List
_#_(*) 	                                     -- any call with any params
```

**Java↔Kotlin equivalence applies to receiver and parameter types.** So
`java.util.regex.Pattern#matches(java.lang.String,java.lang.CharSequence)` also matches a call
whose argument types are `kotlin.String` and `kotlin.CharSequence`.

**Static Java methods** (no dispatch receiver, e.g. `Pattern.matches(...)`) are matched by
the class prefix of the fully-qualified callee name.

---

## Pre-Analysis Requirement

`typeIs` and `matchesSig` rely on a `KotlinTypeAnalysisContext` that must be populated
**before** PMD runs its analysis. The context holds a pre-computed index of all resolved
declarations and call sites, produced by [kotlin-type-mapper](https://github.com/stokpop/kotlin-type-mapper).

### In tests

Use `KotlinTypeXPathTestHelper` to run the analyzer and inject the context:

```java
@BeforeEach
void setUp() {
    helper = KotlinTypeXPathTestHelper.forDirectory(new File(resourceDir));
    helper.injectContext();   // runs kotlin-type-mapper, sets global context
}

@AfterEach
void tearDown() {
    KotlinTypeAnalysisContextHolder.clearGlobal();
}
```

### In production

Run `kotlin-type-mapper` as a pre-analysis step (e.g. in your build) to produce a `TypedAst`
serialized to JSON, then wire it to `KotlinTypeAnalysisContextHolder.setGlobal(...)` via a
PMD language property or a custom `KotlinHandler` extension. Without a context, both functions
return `false` for all nodes (no false positives, no analysis).

---

## Example Rules

### Avoid `DecimalFormat` as a field (`errorprone.xml`)

```xml
<rule name="AvoidDecimalFormatAsField"
      language="kotlin"
      message="DecimalFormat is not thread-safe; avoid storing it as a field."
      class="net.sourceforge.pmd.lang.rule.xpath.XPathRule">
  <description>...</description>
  <properties>
    <property name="xpath">
      <value><![CDATA[
        //PropertyDeclaration[
            not(ancestor::FunctionBody)
            and (pmd-kotlin:typeIs('java.text.DecimalFormat')
              or pmd-kotlin:typeIs('java.text.ChoiceFormat')
              or pmd-kotlin:typeIs('java.text.NumberFormat'))
        ]
      ]]></value>
    </property>
  </properties>
</rule>
```

### Avoid recompiling `Pattern` inside a function (`bestpractices.xml`)

```xml
<rule name="AvoidRecompilingPatterns"
      language="kotlin"
      message="Avoid compiling Pattern inside a function; use a companion object or top-level val."
      class="net.sourceforge.pmd.lang.rule.xpath.XPathRule">
  <description>...</description>
  <properties>
    <property name="xpath">
      <value><![CDATA[
        //FunctionBody//PostfixUnaryExpression[
            pmd-kotlin:matchesSig('java.util.regex.Pattern#compile(_)')
        ]
      ]]></value>
    </property>
  </properties>
</rule>
```

---

## Known Limitations

- Requires pre-computed type analysis. Without it, both functions return `false` (safe default).
- Analysis is based on kotlin-type-mapper's K1 PSI; line numbers may differ by ±1 from PMD's
  ANTLR parser for some property declarations. A ±1 fallback is applied automatically.
- `typeIs` works on both class-level and local (function-body) `PropertyDeclaration` nodes,
  as well as `FunctionDeclaration` nodes. Kotlin uses the same grammar rule for both, and
  kotlin-type-mapper indexes all `KtProperty` nodes regardless of scope.
- Generic type arguments are supported in signatures (e.g. `kotlin.collections.List<kotlin.String>`),
  but omitting the type argument matches the raw (erased) type.
