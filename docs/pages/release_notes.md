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

### 🌟️ New Rules
* The new Apex rule {% rule apex/errorprone/AvoidInterfaceAsMapKey %} reports `Map` declarations
  (fields, variables, parameters) whose key type is an interface that has at least one abstract implementing
  class defining `equals` or `hashCode`. Using such maps results in potentially duplicated map entries or
  not being able to get entries by key.
* The new Java rule {% rule java/multithreading/OverridingThreadRun %} finds overridden `Thread::run` methods.
  This is not recommended. Instead, implement `Runnable` and pass an instance to the thread constructor.

### 🐛️ Fixed Issues
* apex
  * [#5386](https://github.com/pmd/pmd/issues/5386): \[apex] Apex files ending in "Test" are skipped with a number of rules
* apex-errorprone
  * [#6492](https://github.com/pmd/pmd/issues/6492): \[apex] New rule: Prevent use of interface -&gt; abstract class with equals/hashCode as key in Map
* apex-security
  * [#5385](https://github.com/pmd/pmd/issues/5385): \[apex] ApexCRUDViolation not reported even if SOQL doesn't have permissions check on it
* java-bestpractices
  * [#4272](https://github.com/pmd/pmd/issues/4272): \[java] JUnitTestsShouldIncludeAssert: False positive with assert in lambda
* java-multithreading
  * [#595](https://github.com/pmd/pmd/issues/595): \[java] New rule: Implement Runnable instead of extending Thread
* kotlin
  * [#6003](https://github.com/pmd/pmd/issues/6003): \[kotlin] Support multidollar interpolation (Kotlin 2.2)

### 🚨️ API Changes

### ✨️ Merged pull requests
<!-- content will be automatically generated, see /do-release.sh -->
* [#6493](https://github.com/pmd/pmd/pull/6493): \[apex] New Rule: AvoidInterfaceAsMapKeyRule - [Jonny Alexander Power](https://github.com/JonnyPower) (@JonnyPower)
* [#6497](https://github.com/pmd/pmd/pull/6497): \[kotlin] Fix kotlin grammar for parsing multidollar interpolation - [Peter Paul Bakker](https://github.com/stokpop) (@stokpop)
* [#6555](https://github.com/pmd/pmd/pull/6555): \[java] New rule: OverridingThreadRun to prefer using Runnable - [Zbynek Konecny](https://github.com/zbynek) (@zbynek)
* [#6556](https://github.com/pmd/pmd/pull/6556): \[java] Fix #4272: False positive in UnitTestShouldIncludeAssert when using assertion in lambda - [Lukas Gräf](https://github.com/lukasgraef) (@lukasgraef)
* [#6563](https://github.com/pmd/pmd/pull/6563): \[apex] Remove class name suffix "Test" as indicator of test classes - [David Schach](https://github.com/dschach) (@dschach)
* [#6576](https://github.com/pmd/pmd/pull/6576): \[test] chore: Throw a TestAbortedException on disabled tests - [UncleOwen](https://github.com/UncleOwen) (@UncleOwen)
* [#6577](https://github.com/pmd/pmd/pull/6577): \[dist] chore: Improve error message for missing JAVA_HOME in AntIT.java - [UncleOwen](https://github.com/UncleOwen) (@UncleOwen)
* [#6607](https://github.com/pmd/pmd/pull/6607): \[doc] basic.xml has been gone for a long time - [UncleOwen](https://github.com/UncleOwen) (@UncleOwen)

### 📦️ Dependency updates
<!-- content will be automatically generated, see /do-release.sh -->

### 📈️ Stats
<!-- content will be automatically generated, see /do-release.sh -->

{% endtocmaker %}

