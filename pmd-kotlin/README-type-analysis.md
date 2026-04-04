# Kotlin Type Analysis — `pmd-kotlin:typeIs` and `pmd-kotlin:matchesSig`

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
