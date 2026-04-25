---
title: PMD Release Notes
permalink: pmd_release_notes.html
keywords: changelog, release notes
---

{% if is_release_notes_processor %}
{% comment %}
This allows to use links e.g. [Basic CLI usage]({{ baseurl }}pmd_userdocs_installation.html) that work both
in the release notes on GitHub (as an absolute url) and on the rendered documentation page (as a relative url).
{% endcomment %}
{% capture baseurl %}https://docs.pmd-code.org/pmd-doc-{{ site.pmd.version }}/{% endcapture %}
{% else %}
{% assign baseurl = "" %}
{% endif %}

## {{ site.pmd.date | date: "%d-%B-%Y" }} - {{ site.pmd.version }}

The PMD team is pleased to announce PMD {{ site.pmd.version }}.

This is a {{ site.pmd.release_type }} release.

{% tocmaker is_release_notes_processor %}

### 🚀️ New and noteworthy

#### New Kotlin rules and XPath helper

New `pmd-kotlin:nodeText()` XPath function that returns the raw source text of the
context node. This enables literal value checks in XPath rules (e.g. single-char string
arguments, numeric literals) where no typed AST accessor exists.

New rules for Kotlin:

**Best practices**
* [`LooseCoupling`]({{ baseurl }}pmd_rules_kotlin_bestpractices.html#loosecoupling): Avoid
  declaring class-level fields with concrete collection types (ArrayList, HashMap, HashSet, etc.).
  Prefer MutableList, MutableMap, or MutableSet interfaces for better flexibility.

**Error prone**
* [`AvoidCatchingNPE`]({{ baseurl }}pmd_rules_kotlin_errorprone.html#avoidcatchingnpe): Catching
  NullPointerException is a code smell in Kotlin. Use null-safety operators (?., ?:, !!) instead.
* [`DoNotTerminateVM`]({{ baseurl }}pmd_rules_kotlin_errorprone.html#donotterminatevm): Avoid
  calling System.exit() or Runtime.halt(). Throw an exception or use a shutdown hook instead.
* [`DoNotExtendJavaLangError`]({{ baseurl }}pmd_rules_kotlin_errorprone.html#donotextendjavalang): Do
  not extend Error or its standard subclasses; extend Exception or RuntimeException instead.
* [`UseLocaleWithCaseConversions`]({{ baseurl }}pmd_rules_kotlin_errorprone.html#uselocalewithcaseconversions):
  String.toLowerCase()/toUpperCase() use the default system Locale. Use Kotlin's locale-safe
  lowercase()/uppercase() extensions instead.

**Performance**
* [`AppendCharacterWithChar`]({{ baseurl }}pmd_rules_kotlin_performance.html#appendcharacterwithchar):
  Replace single-char String literals in StringBuilder.append() with Char literals to avoid
  unnecessary String allocation.
* [`AvoidCallingGcExplicitly`]({{ baseurl }}pmd_rules_kotlin_performance.html#avoidcallinggcexplicitly):
  Avoid calling System.gc() or Runtime.getRuntime().gc(). These are unreliable and degrade throughput.
* [`BigIntegerInstantiation`]({{ baseurl }}pmd_rules_kotlin_performance.html#bigintegerinstantiation):
  Use BigInteger.ZERO, BigInteger.ONE, or BigInteger.TEN instead of BigInteger.valueOf(0/1/10).
* [`UseIndexOfChar`]({{ baseurl }}pmd_rules_kotlin_performance.html#useindexofchar): Use
  indexOf(Char) / lastIndexOf(Char) instead of indexOf(String) / lastIndexOf(String) for
  single-character arguments.
* [`UseStringBuilderLength`]({{ baseurl }}pmd_rules_kotlin_performance.html#usestringbuilderlength):
  Use StringBuilder.length directly instead of StringBuilder.toString().length to avoid
  allocating a temporary String.

#### More new Kotlin rules (second batch)

**Best practices**
* [`UseCollectionIsEmpty`]({{ baseurl }}pmd_rules_kotlin_bestpractices.html#usecollectionisempty): Replace
  `collection.size == 0` (or `!= 0`) with `collection.isEmpty()` / `collection.isNotEmpty()`.
* [`UseStandardCharsets`]({{ baseurl }}pmd_rules_kotlin_bestpractices.html#usestandardcharsets): Use
  `StandardCharsets.UTF_8` (or similar constants) instead of `Charset.forName("UTF-8")`.

**Error prone**
* [`EqualsNull`]({{ baseurl }}pmd_rules_kotlin_errorprone.html#equalsnull): Replace `x.equals(null)` with
  a direct null check (`x == null`). `equals(null)` always returns false and is likely a bug.
* [`ReplaceJavaUtilDate`]({{ baseurl }}pmd_rules_kotlin_errorprone.html#replacejavautildate): Avoid using
  `java.util.Date`; prefer `java.time.LocalDate`, `LocalDateTime`, or `Instant` instead.
* [`ReturnFromFinallyBlock`]({{ baseurl }}pmd_rules_kotlin_errorprone.html#returnfromfinallyblock): A
  `return` inside a `finally` block silently swallows any exception thrown in the `try` block.
* [`SimpleDateFormatNeedsLocale`]({{ baseurl }}pmd_rules_kotlin_errorprone.html#simpledateformatneedslocale):
  `SimpleDateFormat` constructed without a Locale uses the default system Locale and can produce
  locale-sensitive output unexpectedly.
* [`UnnecessaryCaseChange`]({{ baseurl }}pmd_rules_kotlin_errorprone.html#unnecessarycasechange): Calling
  `.toLowerCase().equals(...)` or `.toUpperCase().equals(...)` is redundant; use
  `.equals(..., ignoreCase = true)` instead.

**Performance**
* [`AddEmptyString`]({{ baseurl }}pmd_rules_kotlin_performance.html#addemptystring): Avoid appending an
  empty string literal `""` to convert a value to String; use `.toString()` or string templates instead.
* [`ConsecutiveLiteralAppends`]({{ baseurl }}pmd_rules_kotlin_performance.html#consecutiveliteralappends):
  Multiple consecutive `StringBuilder.append()` calls with string literals should be combined into a
  single `append()` call to reduce method-call overhead.
* [`UselessStringValueOf`]({{ baseurl }}pmd_rules_kotlin_performance.html#uselessstringvalueof): Avoid
  wrapping a value in `String.valueOf()`; use a string template or `.toString()` instead.

### 🐛️ Fixed Issues

### 🚨️ API Changes

### ✨️ Merged pull requests
<!-- content will be automatically generated, see /do-release.sh -->

### 📦️ Dependency updates
<!-- content will be automatically generated, see /do-release.sh -->

### 📈️ Stats
<!-- content will be automatically generated, see /do-release.sh -->

{% endtocmaker %}

