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

import nl.stokpop.typemapper.model.AnnotationAst;
import nl.stokpop.typemapper.model.DeclarationAst;
import nl.stokpop.typemapper.model.FileAst;
import nl.stokpop.typemapper.model.TypedAst;

/**
 * Walks a parsed Kotlin AST and sets type/annotation attributes on nodes using
 * pre-analyzed type data from kotlin-type-mapper:
 *
 * <ul>
 *   <li>{@link KotlinNode#TYPE_NAME_KEY} on {@code PropertyDeclaration} nodes (property type)</li>
 *   <li>{@link KotlinNode#RETURN_TYPE_KEY} on {@code FunctionDeclaration} nodes (return type)</li>
 *   <li>{@link KotlinNode#TYPE_NAME_KEY} on {@code KtUnescapedAnnotation} nodes (annotation FQN)</li>
 *   <li>{@link KotlinNode#ANNOTATION_NAMES_KEY} on declaration nodes (comma-joined FQN list)</li>
 * </ul>
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
     * Annotates all {@code PropertyDeclaration}, {@code FunctionDeclaration}, and
     * {@code ClassDeclaration} nodes in the given AST root, and sets {@code @TypeName}
     * on their {@code KtUnescapedAnnotation} children.
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
                        setAnnotationAttributes(node, decl.getAnnotations());
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
                        setAnnotationAttributes(node, decl.getAnnotations());
                        break;
                    }
                }
                return visitChildren(node, data);
            }

            @Override
            public Void visitClassDeclaration(KotlinParser.KtClassDeclaration node, Void data) {
                List<DeclarationAst> decls = lookupWithFallback(byLine, node.getBeginLine());
                for (DeclarationAst decl : decls) {
                    if ("class".equals(decl.getKind())
                            || "data_class".equals(decl.getKind())
                            || "sealed_class".equals(decl.getKind())
                            || "interface".equals(decl.getKind())
                            || "enum".equals(decl.getKind())) {
                        setAnnotationAttributes(node, decl.getAnnotations());
                        break;
                    }
                }
                return visitChildren(node, data);
            }

        }, null);
    }

    /**
     * Sets {@link KotlinNode#ANNOTATION_NAMES_KEY} on the declaration node and
     * {@link KotlinNode#TYPE_NAME_KEY} on each of its {@code KtUnescapedAnnotation}
     * children, matching by simple (unqualified) name.
     */
    private static void setAnnotationAttributes(KotlinNode declNode, List<AnnotationAst> annotations) {
        if (annotations.isEmpty()) {
            return;
        }
        // Build a simple-name → FQN lookup (last match wins; duplicates are rare)
        Map<String, String> simpleToFqn = new HashMap<>();
        StringBuilder fqnList = new StringBuilder();
        for (AnnotationAst ann : annotations) {
            String fqn = ann.getFqName();
            if (fqn == null || fqn.isEmpty()) {
                continue;
            }
            simpleToFqn.put(simpleNameOf(fqn), fqn);
            if (fqnList.length() > 0) {
                fqnList.append(',');
            }
            fqnList.append(fqn);
        }
        if (fqnList.length() > 0) {
            declNode.getUserMap().set(KotlinNode.ANNOTATION_NAMES_KEY, fqnList.toString());
        }

        // Set @TypeName on each KtUnescapedAnnotation in the declaration's modifiers
        List<KotlinParser.KtUnescapedAnnotation> annNodes = collectAnnotationNodes(declNode);
        for (KotlinParser.KtUnescapedAnnotation annNode : annNodes) {
            String writtenName = getAnnotationWrittenName(annNode);
            if (writtenName == null) {
                continue;
            }
            // Try exact FQN match first, then simple-name match
            String fqn = simpleToFqn.get(simpleNameOf(writtenName));
            if (fqn == null) {
                fqn = simpleToFqn.get(writtenName); // handles fully-qualified written name
            }
            if (fqn != null) {
                annNode.getUserMap().set(KotlinNode.TYPE_NAME_KEY, fqn);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Helpers

    /**
     * Collects all {@code KtUnescapedAnnotation} nodes from the direct modifiers
     * of a declaration node (does not recurse into function/class bodies).
     */
    private static List<KotlinParser.KtUnescapedAnnotation> collectAnnotationNodes(KotlinNode declNode) {
        List<KotlinParser.KtUnescapedAnnotation> result = new ArrayList<>();
        for (int i = 0; i < declNode.getNumChildren(); i++) {
            KotlinNode child = declNode.getChild(i);
            if (child instanceof KotlinParser.KtModifiers) {
                collectFromModifiers((KotlinParser.KtModifiers) child, result);
                break;
            }
        }
        return result;
    }

    private static void collectFromModifiers(KotlinParser.KtModifiers mods,
            List<KotlinParser.KtUnescapedAnnotation> result) {
        for (int i = 0; i < mods.getNumChildren(); i++) {
            KotlinNode child = mods.getChild(i);
            if (child instanceof KotlinParser.KtAnnotation) {
                collectFromAnnotationRule((KotlinParser.KtAnnotation) child, result);
            }
        }
    }

    private static void collectFromAnnotationRule(KotlinParser.KtAnnotation ann,
            List<KotlinParser.KtUnescapedAnnotation> result) {
        for (int i = 0; i < ann.getNumChildren(); i++) {
            KotlinNode child = ann.getChild(i);
            if (child instanceof KotlinParser.KtSingleAnnotation) {
                for (int j = 0; j < child.getNumChildren(); j++) {
                    KotlinNode sub = child.getChild(j);
                    if (sub instanceof KotlinParser.KtUnescapedAnnotation) {
                        result.add((KotlinParser.KtUnescapedAnnotation) sub);
                        break;
                    }
                }
            } else if (child instanceof KotlinParser.KtMultiAnnotation) {
                for (int j = 0; j < child.getNumChildren(); j++) {
                    KotlinNode sub = child.getChild(j);
                    if (sub instanceof KotlinParser.KtUnescapedAnnotation) {
                        result.add((KotlinParser.KtUnescapedAnnotation) sub);
                    }
                }
            }
        }
    }

    /**
     * Extracts the annotation name as written in source from a
     * {@code KtUnescapedAnnotation} node, using the text region of the contained
     * {@code KtUserType} node. Returns e.g. {@code "Column"} or
     * {@code "javax.persistence.Column"}, or {@code null} on failure.
     */
    private static String getAnnotationWrittenName(KotlinParser.KtUnescapedAnnotation annNode) {
        KotlinParser.KtUserType userType = findUserType(annNode);
        if (userType == null) {
            return null;
        }
        try {
            return userType.getTextDocument()
                    .sliceOriginalText(userType.getTextRegion())
                    .toString();
        } catch (Exception ignored) {
            return null;
        }
    }

    /** Finds the {@code KtUserType} directly inside a {@code KtUnescapedAnnotation}. */
    private static KotlinParser.KtUserType findUserType(KotlinParser.KtUnescapedAnnotation annNode) {
        for (int i = 0; i < annNode.getNumChildren(); i++) {
            KotlinNode child = annNode.getChild(i);
            if (child instanceof KotlinParser.KtUserType) {
                return (KotlinParser.KtUserType) child;
            }
            if (child instanceof KotlinParser.KtConstructorInvocation) {
                for (int j = 0; j < child.getNumChildren(); j++) {
                    if (child.getChild(j) instanceof KotlinParser.KtUserType) {
                        return (KotlinParser.KtUserType) child.getChild(j);
                    }
                }
            }
        }
        return null;
    }

    private static String simpleNameOf(String name) {
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1) : name;
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
