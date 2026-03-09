/*
 * BSD-style license; for more info see http://pmd.sourceforge.net/license.html
 */

package net.sourceforge.pmd.lang.kotlin.ast;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

import net.sourceforge.pmd.lang.kotlin.ast.KotlinParser.KtKotlinFile;
import net.sourceforge.pmd.lang.kotlin.ast.KotlinParser.KtMultiLineStringContent;
import net.sourceforge.pmd.lang.kotlin.ast.KotlinParser.KtMultiLineStringExpression;
import net.sourceforge.pmd.lang.kotlin.ast.KotlinParser.KtMultiLineStringLiteral;



/**
 * Minimal test that parses a Kotlin snippets with new syntax and see if there
 * are no parsing issues.
 */
class KotlinParserNewSyntaxTest {

    public static final int BUFFER_BYTES = 8 * 1024;

    private static byte[] readAllBytesCompat(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[BUFFER_BYTES];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    private static String readResource(String classpathPath) {
        try (InputStream in = KotlinParserNewSyntaxTest.class.getClassLoader().getResourceAsStream(classpathPath)) {
            if (in == null) {
                throw new IllegalArgumentException("Test resource not found on classpath: " + classpathPath);
            }
            return new String(readAllBytesCompat(in), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void testMultiLineStringRefs() {
        String code = readResource("net/sourceforge/pmd/lang/kotlin/ast/testdata/MultiLineStringRefs.kt");

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
                                    && ((KotlinTerminalNode) c.getFirstChild()).getFirstAntlrToken().getType() == KotlinParser.MultiLineStrRef)
                            .count();
        assertEquals(2, refCount, "Expected two multi-line string refs ($$serviceField and $$$productName)");

        // There is no `${...}` / `$$${...}` in this snippet.
        assertEquals(0, root.descendants(KtMultiLineStringExpression.class).count(), "Expected no multi-line string expression entries");
    }

    @Test
    void testMultiLineStringExpressions() {
        String code = readResource("net/sourceforge/pmd/lang/kotlin/ast/testdata/MultiLineStringExpressions.kt");

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
