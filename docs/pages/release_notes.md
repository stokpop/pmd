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

### 🐛️ Fixed Issues
* apex
  * [#5386](https://github.com/pmd/pmd/issues/5386): \[apex] Apex files ending in "Test" are skipped with a number of rules
* apex-security
  * [#5385](https://github.com/pmd/pmd/issues/5385): \[apex] ApexCRUDViolation not reported even if SOQL doesn't have permissions check on it

### 🚨️ API Changes

### ✨️ Merged pull requests
<!-- content will be automatically generated, see /do-release.sh -->
* [#6563](https://github.com/pmd/pmd/pull/6563): \[apex] Remove class name suffix "Test" as indicator of test classes - [David Schach](https://github.com/dschach) (@dschach)
* [#6576](https://github.com/pmd/pmd/pull/6576): \[test] chore: Throw a TestAbortedException on disabled tests - [UncleOwen](https://github.com/UncleOwen) (@UncleOwen)
* [#6577](https://github.com/pmd/pmd/pull/6577): \[dist] chore: Improve error message for missing JAVA_HOME in AntIT.java - [UncleOwen](https://github.com/UncleOwen) (@UncleOwen)

### 📦️ Dependency updates
<!-- content will be automatically generated, see /do-release.sh -->

### 📈️ Stats
<!-- content will be automatically generated, see /do-release.sh -->

{% endtocmaker %}

