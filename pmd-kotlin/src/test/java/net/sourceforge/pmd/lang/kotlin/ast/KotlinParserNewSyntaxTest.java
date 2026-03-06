/*
 * BSD-style license; for more info see http://pmd.sourceforge.net/license.html
 */

package net.sourceforge.pmd.lang.kotlin.ast;

import net.sourceforge.pmd.lang.ast.NodeStream;
import net.sourceforge.pmd.lang.kotlin.ast.KotlinParser.KtKotlinFile;
import org.junit.jupiter.api.Test;

import java.util.List;

import static net.sourceforge.pmd.lang.kotlin.ast.KotlinParser.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Minimal test that parses a Kotlin snippets with new syntax and see if there
 * are no parsing issues.
 */
class KotlinParserNewSyntaxTest {

    @Test
    void testMultiLineStringRefs() {
        String code = "class Foo { fun foo() { val productName = \"carrot\"\n" +
                "val requestedData =\n" +
                "    $$$\"\"\"{\n" +
                "      \"currency\": \"$\",\n" +
                "      \"enteredAmount\": \"42.45 $$\",\n" +
                "      \"$$serviceField\": \"none\",\n" +
                "      \"product\": \"$$$productName\"\n" +
                "    }\n" +
                "    \"\"\" }; }";

        // Parse using KotlinParsingHelper
        KtKotlinFile root = KotlinParsingHelper.DEFAULT.parse(code);

        assertNotNull(root);

        // In this grammar, `$identifier` (and multi-dollar variants like `$$$productName`) are represented
        // as `MultiLineStrRef` tokens inside `KtMultiLineStringContent`. `KtMultiLineStringExpression` is
        // reserved for `$+{...}` forms.
        List<KtMultiLineStringLiteral> literals = root.descendants(KtMultiLineStringLiteral.class).toList();
        assertEquals(1, literals.size(), "Expected exactly one multi-line string literal");

        long refCount = root.descendants(KtMultiLineStringContent.class)
                            .filter(c -> c.getFirstChild() instanceof KotlinTerminalNode
                                    && ((KotlinTerminalNode) c.getFirstChild()).getFirstAntlrToken().getType() == MultiLineStrRef)
                            .count();
        assertEquals(2, refCount, "Expected two multi-line string refs ($$serviceField and $$$productName)");

        // There is no `${...}` / `$$${...}` in this snippet.
        assertEquals(0, root.descendants(KtMultiLineStringExpression.class).count(), "Expected no multi-line string expression entries");
    }

    @Test
    void testMultiLineStringExpressions() {
        String code = "class Foo { fun foo() { val productName = \"carrot\"\n" +
                "val requestedData =\n" +
                "    $$$\"\"\"{\n" +
                "      \"currency\": \"$\",\n" +
                "      \"enteredAmount\": \"42.45 $$\",\n" +
                "      \"$${serviceField.length()}\": \"none\",\n" +
                "      \"product\": \"$$${productName.length()}\"\n" +
                "    }\n" +
                "    \"\"\" }; }";

        // Parse using KotlinParsingHelper
        KtKotlinFile root = KotlinParsingHelper.DEFAULT.parse(code);

        assertNotNull(root);

        // In this grammar, `$identifier` (and multi-dollar variants like `$$$productName`) are represented
        // as `MultiLineStrRef` tokens inside `KtMultiLineStringContent`. `KtMultiLineStringExpression` is
        // reserved for `$+{...}` forms.
        List<KtMultiLineStringLiteral> literals = root.descendants(KtMultiLineStringLiteral.class).toList();
        assertEquals(1, literals.size(), "Expected exactly one multi-line string literal");

        // The `$${...}` / `$$${...}` in this snippet.
        List<KtMultiLineStringExpression> expressions = root.descendants(KtMultiLineStringExpression.class).toList();
        assertEquals(2, expressions.size(), "Expected no multi-line string expression entries");
    }
}
