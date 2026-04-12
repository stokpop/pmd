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

