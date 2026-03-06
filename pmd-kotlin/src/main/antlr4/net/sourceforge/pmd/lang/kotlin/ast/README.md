# Kotlin Grammar

The grammar files for Kotlin are taken from <https://github.com/Kotlin/kotlin-spec>, released under the
Apache License, Version 2.0:

```
Copyright 2000-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
```

The grammar files still use the Apache License, but are slightly modified.
All other files in this PMD module are licensed under BSD.

Updated grammar files are taken from https://github.com/antlr/grammars-v4 which
based its grammar on https://kotlinlang.org/grammar/.
See discussion in the issue: https://github.com/antlr/grammars-v4/issues/3965
Most recent update (March 2026): https://github.com/antlr/grammars-v4/pull/4770

This is an "informal Kotlin grammar" but it might be the best available for PMD purposes.
Not sure what version of Kotlin it is based on, but seems more recent that the previous 1.8 version.


## Currently used version

* Release: <https://github.com/Kotlin/kotlin-spec/releases/tag/v1.8-rfc%2B0.1>
* Source: <https://github.com/Kotlin/kotlin-spec/tree/v1.8-rfc%2B0.1/grammar/src/main/antlr>

Updated version uses files from https://github.com/antlr/grammars-v4:

* src/main/antlr4/imports/UnicodeClasses.g4 is copy of https://github.com/antlr/grammars-v4/blob/master/kotlin/kotlin/KotlinLexer.g4
* src/main/antlr4/net/sourceforge/pmd/lang/kotlin/ast/Kotlin.g4 is copy of https://github.com/antlr/grammars-v4/blob/master/kotlin/kotlin/KotlinParser.g4
* src/main/antlr4/net/sourceforge/pmd/lang/kotlin/ast/KotlinLexer.g4 is copy of https://github.com/antlr/grammars-v4/blob/master/kotlin/kotlin/UnicodeClasses.g4

Added the modification below.

### Modifications

Some modifications are made in KotlinParser.g4:

*   The file "KotlinParser.g4" is renamed to "Kotlin.g4"
*   `grammar Kotlin` instead of KotlinParser
*   Additional headers:

```
@header {
import net.sourceforge.pmd.lang.ast.impl.antlr4.*;
import net.sourceforge.pmd.lang.ast.AstVisitor;
}
```

*   Additional members:

```
@parser::members {

    static final AntlrNameDictionary DICO = new KotlinNameDictionary(VOCABULARY, ruleNames);

    @Override
    protected KotlinTerminalNode createPmdTerminal(ParserRuleContext parent, Token t) {
        return new KotlinTerminalNode(t);
    }

    @Override
    protected KotlinErrorNode createPmdError(ParserRuleContext parent, Token t) {
        return new KotlinErrorNode(t);
    }
}
```

*   Additional options:

```
contextSuperClass = 'KotlinInnerNode';
superClass = 'AntlrGeneratedParserBase<KotlinNode>';
```

## Generate code

To generate code from grammar files:

```shell
./mvnw generate-sources -pl pmd-kotlin
```

## Run tests

To run tests: 

```shell
./mvnw test -pl pmd-kotlin
```

## Developer notes

### Kotlin: multi-`$` string templates

PMD's Kotlin frontend is based on an ANTLR4 lexer/parser in `pmd-kotlin`. The lexer supports Kotlin's *multi-dollar string templates* (sometimes used to embed formats like JSON that contain `$`, without triggering interpolation).

Kotlin allows prefixing a string literal opener with one or more `$` characters:

- `"..."` / `"""..."""`: normal Kotlin interpolation starts with `$name` or `${expr}`
- `$$"..."` / `$$"""..."""`: interpolation starts with `$$name` or `$${expr}`
- `$$$"..."` / `$$$"""..."""`: interpolation starts with `$$$name` or `$$${expr}`

This means that inside a `$$$"""..."""` string, occurrences of `$` or `$$` are treated as plain text, while `$$$identifier` and `$$${...}` start templates.

The lexer rules implementing this live in `pmd-kotlin/src/main/antlr4/net/sourceforge/pmd/lang/kotlin/ast/KotlinLexer.g4`:

- `QUOTE_OPEN` / `TRIPLE_QUOTE_OPEN` accept an optional `$*` prefix
- `FieldIdentifier` accepts `$+Identifier` (so `$$$foo` is a valid template reference)
- `LineStrExprStart` / `MultiLineStrExprStart` accept `$+{` (so `$$${...}` is a valid template expression)

See: https://kotlinlang.org/docs/strings.html#multi-dollar-string-interpolation