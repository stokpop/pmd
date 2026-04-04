/*
 * BSD-style license; for more info see http://pmd.sourceforge.net/license.html
 */

package net.sourceforge.pmd.lang.kotlin.ast;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import nl.stokpop.typemapper.model.DeclarationAst;
import nl.stokpop.typemapper.model.FileAst;
import nl.stokpop.typemapper.model.TypedAst;

/**
 * Walks a parsed Kotlin AST and sets {@link KotlinNode#TYPE_NAME_KEY} /
 * {@link KotlinNode#RETURN_TYPE_KEY} on {@code PropertyDeclaration} and
 * {@code FunctionDeclaration} nodes using pre-analyzed type data from
 * kotlin-type-mapper.
 *
 * <p>The visitor is constructed once per analysis run (from the {@link TypedAst}
 * produced by kotlin-type-mapper) and applied to each file's root node during
 * the post-parse step inside {@code KotlinLanguageProcessor}.
 *
 * <p>File matching uses the <em>base filename</em> (e.g. {@code "Foo.kt"}) rather
 * than the full path, so it works regardless of whether the files were written to
 * a temporary directory or analyzed from their original location.
 */
public final class KotlinTypeAnnotationVisitor {

    /** Map from base filename (e.g. "Foo.kt") → per-line declarations index. */
    private final Map<String, Map<Integer, List<DeclarationAst>>> byFilename;

    public KotlinTypeAnnotationVisitor(TypedAst typedAst) {
        Map<String, Map<Integer, List<DeclarationAst>>> index = new HashMap<>();
        for (FileAst fileAst : typedAst.getFiles()) {
            String name = new File(fileAst.getRelativePath()).getName();
            Map<Integer, List<DeclarationAst>> byLine =
                    index.computeIfAbsent(name, k -> new HashMap<>());
            for (DeclarationAst decl : fileAst.getDeclarations()) {
                byLine.computeIfAbsent(decl.getLine(), k -> new ArrayList<>()).add(decl);
            }
        }
        this.byFilename = index;
    }

    /**
     * Annotates all {@code PropertyDeclaration} and {@code FunctionDeclaration} nodes
     * in the given AST root.
     *
     * @param root     the root node of the parsed Kotlin file
     * @param absPath  the absolute path of the file (used to extract the base filename)
     */
    public void annotate(KotlinNode root, String absPath) {
        String filename = new File(absPath).getName();
        Map<Integer, List<DeclarationAst>> byLine = byFilename.get(filename);
        if (byLine == null) {
            return;
        }

        root.acceptVisitor(new KotlinVisitorBase<Void, Void>() {

            @Override
            public Void visitPropertyDeclaration(KotlinParser.KtPropertyDeclaration node, Void data) {
                List<DeclarationAst> decls = lookupWithFallback(byLine, node.getBeginLine());
                for (DeclarationAst decl : decls) {
                    if (decl.getType() != null) {
                        node.getUserMap().set(KotlinNode.TYPE_NAME_KEY, decl.getType());
                        break;
                    }
                }
                return visitChildren(node, data);
            }

            @Override
            public Void visitFunctionDeclaration(KotlinParser.KtFunctionDeclaration node, Void data) {
                List<DeclarationAst> decls = lookupWithFallback(byLine, node.getBeginLine());
                for (DeclarationAst decl : decls) {
                    if (decl.getReturnType() != null) {
                        node.getUserMap().set(KotlinNode.RETURN_TYPE_KEY, decl.getReturnType());
                        break;
                    }
                }
                return visitChildren(node, data);
            }

        }, null);
    }

    private static List<DeclarationAst> lookupWithFallback(
            Map<Integer, List<DeclarationAst>> byLine, int line) {
        List<DeclarationAst> exact = byLine.get(line);
        if (exact != null && !exact.isEmpty()) {
            return exact;
        }
        List<DeclarationAst> result = new ArrayList<>();
        List<DeclarationAst> prev = byLine.get(line - 1);
        List<DeclarationAst> next = byLine.get(line + 1);
        if (prev != null) {
            result.addAll(prev);
        }
        if (next != null) {
            result.addAll(next);
        }
        return result.isEmpty() ? Collections.emptyList() : result;
    }
}
