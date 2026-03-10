/*
 * BSD-style license; for more info see http://pmd.sourceforge.net/license.html
 */

package net.sourceforge.pmd.lang.kotlin.ast;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import org.jetbrains.annotations.NotNull;

import net.sourceforge.pmd.lang.kotlin.KotlinLanguageModule;
import net.sourceforge.pmd.lang.test.ast.BaseParsingHelper;

/**
 *  Parsing helper for Kotlin tests.
 */
public class KotlinParsingHelper extends BaseParsingHelper<KotlinParsingHelper, KotlinParser.KtKotlinFile> {

    public static final KotlinParsingHelper DEFAULT = new KotlinParsingHelper(Params.getDefault());
    public static final int BUFFER_BYTES = 8 * 1024;

    public KotlinParsingHelper(@NotNull Params params) {
        super(KotlinLanguageModule.getInstance(), KotlinParser.KtKotlinFile.class, params);
    }

    private static byte[] readAllBytes(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[BUFFER_BYTES];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    public static String readResourcePath(String path) {
        try (InputStream in = KotlinParsingHelper.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalArgumentException("Test resource not found on classpath: " + path);
            }
            return new String(readAllBytes(in), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @NotNull
    @Override
    protected KotlinParsingHelper clone(@NotNull Params params) {
        return new KotlinParsingHelper(params);
    }
}
