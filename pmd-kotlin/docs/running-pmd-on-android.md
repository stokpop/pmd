# Running PMD on Android Kotlin Projects

This guide explains how to run PMD with Kotlin rules against an Android project,
including how to build a complete classpath so that type-aware rules
(e.g. `UnresolvedType`) produce accurate results.

## Prerequisites

- PMD 7.24+ with `pmd-kotlin` installed
- Android SDK installed (see [Install Android SDK](#install-android-sdk))
- Gradle wrapper present in the Android project
- Java 17 or 21 (the Kotlin Gradle plugin does not support Java 25+)

## Install Android SDK

If you do not have the Android SDK, use `sdkmanager` from the command-line tools:

```bash
# Download command-line tools from https://developer.android.com/studio#command-tools
# Extract to ~/Android/Sdk/cmdline-tools/latest

export ANDROID_SDK_ROOT=~/Android/Sdk
sdkmanager "platforms;android-35"
```

Create `local.properties` in the project root:

```bash
echo "sdk.dir=$HOME/Android/Sdk" > local.properties
```

## Build the Project

PMD needs compiled class files on its classpath. Build at least the debug variant:

```bash
# Switch to Java 17 or 21 if needed.
# sdkman example (source init first if not in .bashrc):
source "$HOME/.sdkman/bin/sdkman-init.sh"
sdk use java 21      # picks the latest Java 21 managed by sdkman

# or set JAVA_HOME directly:
export JAVA_HOME=/path/to/jdk-21

./gradlew :app:compileDebugKotlin :app:compileDebugJavaWithJavac
```

> **Note:** `compileDebugJavaWithJavac` is needed to compile generated Java sources
> such as `BuildConfig`. Without it, those imports will be flagged as `UnresolvedType`.

## Collect the Classpath

Add a helper task to `app/build.gradle.kts` to print all resolved compile JARs:

```kotlin
tasks.register("printAndroidClasspath") {
    dependsOn("compileDebugKotlin")
    doLast {
        val variant = android.applicationVariants.find { it.name == "debug" }!!
        variant.getCompileClasspath(null).files.forEach { println(it.absolutePath) }
    }
}
```

Run it and save the output. Set `ANDROID_SDK_ROOT` explicitly here so all
subsequent commands in this section work regardless of shell environment:

```bash
export ANDROID_SDK_ROOT=~/Android/Sdk   # adjust if SDK is elsewhere

./gradlew :app:printAndroidClasspath -q 2>&1 \
  | grep "\.jar$" > /tmp/android_classpath.txt
```

> **Tip:** The `printAndroidClasspath` task uses Gradle's transform pipeline, which
> automatically extracts `classes.jar` from AAR dependencies. This is why it returns
> more entries than a simple `debugCompileClasspath` resolution would.
>
> **Important:** Some libraries (e.g. `okhttp-dnsoverhttps`) depend on transitive JARs
> (e.g. `okhttp-jvm`) that may not appear in the compile classpath directly. If PMD
> throws `NoClassDefFoundError` during analysis, find and add the missing transitive JAR:
>
> ```bash
> find ~/.gradle/caches -name "okhttp-jvm-*.jar" >> /tmp/android_classpath.txt
> ```

Then append the Android platform JAR and your compiled project classes:

```bash
echo "$ANDROID_SDK_ROOT/platforms/android-35/android.jar" >> /tmp/android_classpath.txt
echo "$PWD/app/build/tmp/kotlin-classes/debug"            >> /tmp/android_classpath.txt
echo "$PWD/app/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes" \
                                                           >> /tmp/android_classpath.txt
```

> **Warning:** If `ANDROID_SDK_ROOT` is not set, `android.jar` will be silently added
> with a broken path and PMD will crash with `NoClassDefFoundError: android/...`.
> PMD logs a `[WARN] Skipping invalid Kotlin aux classpath entry` when this happens.
> Always verify the path resolves: `ls "$ANDROID_SDK_ROOT/platforms/android-35/android.jar"`

Build a colon-separated string:

```bash
CLASSPATH=$(paste -sd ':' /tmp/android_classpath.txt)
```

## Run PMD

Create a file list. Skip files over 50,000 bytes to avoid ANTLR parse timeouts on very large generated files:

```bash
find app/src/main/java -name "*.kt" \
  | while read f; do
      [[ $(wc -c < "$f") -lt 50000 ]] && echo "$f"
    done > /tmp/kt_files.txt
```

Run PMD:

```bash
RULESETS="category/kotlin/bestpractices.xml,category/kotlin/design.xml,category/kotlin/errorprone.xml,category/kotlin/multithreading.xml,category/kotlin/performance.xml"

pmd check \
  --file-list /tmp/kt_files.txt \
  --rulesets "$RULESETS" \
  --aux-classpath "$CLASSPATH" \
  --no-progress \
  --format text
```

> **Note:** The available Kotlin rulesets are: `bestpractices`, `design`, `errorprone`,
> `multithreading`, `performance`. There is no `codestyle` ruleset — omit it.

## Understanding `UnresolvedType` Findings

The `UnresolvedType` rule (in `errorprone.xml`) fires on `import` statements
whose type cannot be resolved by the kotlin-type-mapper. Common causes:

| Cause | Fix |
|-------|-----|
| Android SDK missing | Add `android.jar` to `--aux-classpath` |
| AAR dependencies not extracted | Use `printAndroidClasspath` task (extracts AARs via Gradle transforms) |
| Project-internal classes missing | Add `build/tmp/kotlin-classes/debug` to `--aux-classpath` |
| Generated sources (e.g. `BuildConfig`) | Run `compileDebugJavaWithJavac` and add `build/intermediates/javac/.../classes` |
| Truly missing 3rd-party dependencies | Add the missing JAR or suppress the rule |

Without a classpath, nearly every file will produce `UnresolvedType` findings.
With a complete classpath (as above), only genuinely missing dependencies remain.

## Example Results

Running against a medium-sized Android project (~400 Kotlin files, files <50 KB):

| Classpath | UnresolvedType findings |
|-----------|------------------------|
| None | ~4,800 |
| Android SDK only (`android.jar`) | ~4,200 |
| SDK + AAR-extracted JARs | ~300 |
| SDK + AARs + compiled project classes | 0 |

With a complete classpath all 473 findings were genuine code quality issues — no noise.

## Suppressing `UnresolvedType` for Known Missing Deps

Add a `@Suppress` annotation or use a PMD suppress comment:

```kotlin
import com.example.missing.Dep // NOPMD - dependency not available at analysis time
```

Or exclude the rule entirely for generated/vendor packages via a ruleset override:

```xml
<rule ref="category/kotlin/errorprone.xml/UnresolvedType">
  <properties>
    <property name="violationSuppressXPath"
              value="//ImportHeader[starts-with(@Image, 'com.example.generated')]"/>
  </properties>
</rule>
```
