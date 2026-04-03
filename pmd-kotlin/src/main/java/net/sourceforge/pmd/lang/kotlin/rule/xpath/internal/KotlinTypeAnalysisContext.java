/*
 * BSD-style license; for more info see http://pmd.sourceforge.net/license.html
 */

package net.sourceforge.pmd.lang.kotlin.rule.xpath.internal;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import nl.stokpop.typemapper.model.CallSiteAst;
import nl.stokpop.typemapper.model.DeclarationAst;
import nl.stokpop.typemapper.model.FileAst;
import nl.stokpop.typemapper.model.TypedAst;
import nl.stokpop.typemapper.model.TypeNameUtilsKt;

/**
 * Holds pre-analyzed Kotlin type information from kotlin-type-mapper, indexed by
 * (absolute file path, line number) for fast lookup during XPath function evaluation.
 */
public final class KotlinTypeAnalysisContext {

    private static final KotlinTypeAnalysisContext EMPTY = new KotlinTypeAnalysisContext(
            Collections.emptyMap(), Collections.emptyMap());

    /** Map from absolute file path → line → list of call sites on that line. */
    private final Map<String, Map<Integer, List<CallSiteAst>>> callIndex;

    /** Map from absolute file path → line → list of declarations starting on that line. */
    private final Map<String, Map<Integer, List<DeclarationAst>>> declIndex;

    private KotlinTypeAnalysisContext(
            Map<String, Map<Integer, List<CallSiteAst>>> callIndex,
            Map<String, Map<Integer, List<DeclarationAst>>> declIndex) {
        this.callIndex = callIndex;
        this.declIndex = declIndex;
    }

    /** Returns a no-op context (all lookups return empty lists). */
    public static KotlinTypeAnalysisContext empty() {
        return EMPTY;
    }

    /** Builds a context from a {@link TypedAst}, indexing all call sites and declarations. */
    public static KotlinTypeAnalysisContext from(TypedAst ast) {
        Map<String, Map<Integer, List<CallSiteAst>>> callIdx = new HashMap<>();
        Map<String, Map<Integer, List<DeclarationAst>>> declIdx = new HashMap<>();

        String sourceRoot = ast.getSourceRoot();
        for (FileAst file : ast.getFiles()) {
            String absPath = canonicalize(sourceRoot + File.separator + file.getRelativePath());

            for (CallSiteAst call : file.getCalls()) {
                callIdx.computeIfAbsent(absPath, k -> new HashMap<>())
                       .computeIfAbsent(call.getLine(), k -> new ArrayList<>())
                       .add(call);
            }
            for (DeclarationAst decl : file.getDeclarations()) {
                declIdx.computeIfAbsent(absPath, k -> new HashMap<>())
                       .computeIfAbsent(decl.getLine(), k -> new ArrayList<>())
                       .add(decl);
            }
        }
        return new KotlinTypeAnalysisContext(callIdx, declIdx);
    }

    /**
     * Returns call sites recorded at the given file and line.
     * If the exact line has no entries, also checks line ± 1 to tolerate minor
     * line-number differences between PMD's ANTLR parser and kotlin-type-mapper's PSI.
     */
    public List<CallSiteAst> callSitesAt(String absFilePath, int line) {
        Map<Integer, List<CallSiteAst>> byLine = callIndex.get(absFilePath);
        if (byLine == null) {
            return Collections.emptyList();
        }
        List<CallSiteAst> exact = byLine.get(line);
        if (exact != null && !exact.isEmpty()) {
            return exact;
        }
        // ±1 line fallback
        List<CallSiteAst> result = new ArrayList<>();
        List<CallSiteAst> prev = byLine.get(line - 1);
        List<CallSiteAst> next = byLine.get(line + 1);
        if (prev != null) {
            result.addAll(prev);
        }
        if (next != null) {
            result.addAll(next);
        }
        return result;
    }

    /**
     * Returns declarations recorded at the given file and line.
     * Also checks line ± 1 as a fallback.
     */
    public List<DeclarationAst> declarationsAt(String absFilePath, int line) {
        Map<Integer, List<DeclarationAst>> byLine = declIndex.get(absFilePath);
        if (byLine == null) {
            return Collections.emptyList();
        }
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
        return result;
    }

    /**
     * Returns true if {@code expected} and {@code actual} refer to the same type,
     * accounting for Java↔Kotlin name mapping (e.g. java.lang.String ↔ kotlin.String).
     */
    public boolean isTypeEquivalent(String expected, String actual) {
        return TypeNameUtilsKt.typeNamesEquivalent(expected, actual);
    }

    private static String canonicalize(String path) {
        try {
            return new File(path).getCanonicalPath();
        } catch (java.io.IOException e) {
            return new File(path).getAbsolutePath();
        }
    }
}
