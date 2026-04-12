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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import nl.stokpop.typemapper.model.AnnotationAst;
import nl.stokpop.typemapper.model.DeclarationAst;
import nl.stokpop.typemapper.model.DeclarationKind;
import nl.stokpop.typemapper.model.FileAst;
import nl.stokpop.typemapper.model.ParameterAst;
import nl.stokpop.typemapper.model.TypedAst;

/**
 * Walks a parsed Kotlin AST and sets type/annotation attributes on nodes using
 * pre-analyzed type data from kotlin-type-mapper:
 *
 * <ul>
 *   <li>{@link KotlinNode#TYPE_NAME_KEY} on {@code PropertyDeclaration} nodes (property type)</li>
 *   <li>{@link KotlinNode#TYPE_NAME_KEY} on {@code ClassParameter} nodes — primary constructor
 *       {@code val}/{@code var} params (e.g. {@code class Foo(val name: String)})</li>
 *   <li>{@link KotlinNode#RETURN_TYPE_KEY} on {@code FunctionDeclaration} nodes (return type)</li>
 *   <li>{@link KotlinNode#TYPE_NAME_KEY} on {@code FunctionValueParameter} nodes (parameter type)</li>
 *   <li>{@link KotlinNode#TYPE_NAME_KEY} on {@code CatchBlock} nodes (caught exception type)</li>
 *   <li>{@link KotlinNode#TYPE_NAME_KEY} on {@code ForStatement} nodes (loop variable type)</li>
 *   <li>{@link KotlinNode#TYPE_NAME_KEY} on {@code UnescapedAnnotation} <em>and</em>
 *       {@code SingleAnnotation} nodes (annotation FQN — set on both for convenience)</li>
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

    private static final Logger LOG = LoggerFactory.getLogger(KotlinTypeAnnotationVisitor.class);

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
     * Annotates all {@code PropertyDeclaration}, {@code FunctionDeclaration},
     * {@code ClassDeclaration}, {@code CatchBlock}, and {@code ForStatement} nodes in
     * the given AST root, and sets {@code @TypeName} on their annotation children
     * as well as on {@code FunctionValueParameter} children of function declarations.
     *
     * @param root     the root node of the parsed Kotlin file
     * @param absPath  the absolute path of the file (used to extract the base filename)
     */
    public void annotate(KotlinNode root, String absPath) {
        String filename = new File(absPath).getName();
        Map<Integer, List<DeclarationAst>> resolved = byFilename.get(filename);
        if (resolved == null && !filename.endsWith(".kt")) {
            // Fallback: PmdRuleTst uses synthetic file ids without .kt extension (e.g. "file").
            // The temp file written to disk has .kt appended, so try that name.
            resolved = byFilename.get(filename + ".kt");
        }
        if (resolved == null) {
            return;
        }
        final Map<Integer, List<DeclarationAst>> byLine = resolved;

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

            // Primary constructor val/var parameters (e.g. "class Foo(val name: String)")
            // are KtClassParameter nodes in the AST, not KtPropertyDeclaration.
            // kotlin-type-mapper emits them as kind="property" with a type field.
            @Override
            public Void visitClassParameter(KotlinParser.KtClassParameter node, Void data) {
                List<DeclarationAst> decls = lookupWithFallback(byLine, node.getBeginLine());
                for (DeclarationAst decl : decls) {
                    if (DeclarationKind.PROPERTY.equals(decl.getKind()) && decl.getType() != null) {
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
                        setFunctionParameterTypes(node, decl.getParameters());
                        break;
                    }
                }
                return visitChildren(node, data);
            }

            @Override
            public Void visitCatchBlock(KotlinParser.KtCatchBlock node, Void data) {
                List<DeclarationAst> decls = lookupWithFallback(byLine, node.getBeginLine());
                for (DeclarationAst decl : decls) {
                    if (DeclarationKind.CATCH_VARIABLE.equals(decl.getKind()) && decl.getType() != null) {
                        node.getUserMap().set(KotlinNode.TYPE_NAME_KEY, decl.getType());
                        break;
                    }
                }
                return visitChildren(node, data);
            }

            @Override
            public Void visitForStatement(KotlinParser.KtForStatement node, Void data) {
                List<DeclarationAst> decls = lookupWithFallback(byLine, node.getBeginLine());
                for (DeclarationAst decl : decls) {
                    if (DeclarationKind.FOR_LOOP_VARIABLE.equals(decl.getKind()) && decl.getType() != null) {
                        node.getUserMap().set(KotlinNode.TYPE_NAME_KEY, decl.getType());
                        break;
                    }
                }
                return visitChildren(node, data);
            }

            @Override
            public Void visitClassDeclaration(KotlinParser.KtClassDeclaration node, Void data) {
                List<DeclarationAst> decls = lookupWithFallback(byLine, node.getBeginLine());
                for (DeclarationAst decl : decls) {
                    if (DeclarationKind.CLASS.equals(decl.getKind())
                            || DeclarationKind.DATA_CLASS.equals(decl.getKind())
                            || DeclarationKind.SEALED_CLASS.equals(decl.getKind())
                            || DeclarationKind.INTERFACE.equals(decl.getKind())
                            || DeclarationKind.ENUM.equals(decl.getKind())) {
                        // Set @TypeName to the class's own FQN (useful in Designer + XPath)
                        node.getUserMap().set(KotlinNode.TYPE_NAME_KEY, decl.getFqName());
                        setAnnotationAttributes(node, decl.getAnnotations());
                        setDelegationSpecifierTypes(node, decl.getSuperTypes());
                        break;
                    }
                }
                return visitChildren(node, data);
            }

        }, null);
    }

    /**
     * Sets {@link KotlinNode#TYPE_NAME_KEY} on each {@code KtDelegationSpecifier}
     * node inside the class declaration, matching the written supertype name (e.g.
     * {@code Serializable}) against the FQNs in {@code superTypes}.
     */
    private static void setDelegationSpecifierTypes(KotlinParser.KtClassDeclaration classNode,
            List<String> superTypes) {
        if (superTypes.isEmpty()) {
            return;
        }
        Map<String, String> simpleToFqn = new HashMap<>();
        for (String fqn : superTypes) {
            simpleToFqn.put(simpleNameOf(fqn), fqn);
        }
        for (int i = 0; i < classNode.getNumChildren(); i++) {
            KotlinNode child = classNode.getChild(i);
            if (child instanceof KotlinParser.KtDelegationSpecifiers) {
                for (int j = 0; j < child.getNumChildren(); j++) {
                    KotlinNode spec = child.getChild(j);
                    if (spec instanceof KotlinParser.KtAnnotatedDelegationSpecifier) {
                        for (int k = 0; k < spec.getNumChildren(); k++) {
                            KotlinNode inner = spec.getChild(k);
                            if (inner instanceof KotlinParser.KtDelegationSpecifier) {
                                annotateDelegationSpecifier(
                                        (KotlinParser.KtDelegationSpecifier) inner, simpleToFqn);
                            }
                        }
                    }
                }
                break;
            }
        }
    }

    /**
     * Sets {@link KotlinNode#TYPE_NAME_KEY} on a single {@code KtDelegationSpecifier}
     * by extracting the written type name from its contained {@code KtUserType}.
     */
    private static void annotateDelegationSpecifier(KotlinParser.KtDelegationSpecifier spec,
            Map<String, String> simpleToFqn) {
        KotlinParser.KtUserType userType = findUserTypeInDelegationSpecifier(spec);
        if (userType == null) {
            return;
        }
        try {
            String written = userType.getTextDocument()
                    .sliceOriginalText(userType.getTextRegion())
                    .toString();
            int angle = written.indexOf('<');
            if (angle >= 0) {
                written = written.substring(0, angle).trim();
            }
            String fqn = simpleToFqn.get(simpleNameOf(written));
            if (fqn != null) {
                spec.getUserMap().set(KotlinNode.TYPE_NAME_KEY, fqn);
            }
        } catch (Exception e) {
            LOG.debug("Could not read text region for delegation specifier in {}", spec, e);
        }
    }

    /**
     * Finds the {@code KtUserType} inside a {@code KtDelegationSpecifier}.
     * Handles both direct {@code userType()} cases and {@code constructorInvocation()}
     * (superclass with constructor call).
     */
    private static KotlinParser.KtUserType findUserTypeInDelegationSpecifier(
            KotlinParser.KtDelegationSpecifier spec) {
        for (int i = 0; i < spec.getNumChildren(); i++) {
            KotlinNode child = spec.getChild(i);
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

    /**
     * Sets {@link KotlinNode#TYPE_NAME_KEY} on each {@code KtFunctionValueParameter}
     * child of the given function declaration, matching by position against the
     * {@code parameters} list from the kotlin-type-mapper {@link DeclarationAst}.
     */
    private static void setFunctionParameterTypes(KotlinParser.KtFunctionDeclaration funcNode,
            List<ParameterAst> parameters) {
        if (parameters.isEmpty()) {
            return;
        }
        for (int i = 0; i < funcNode.getNumChildren(); i++) {
            KotlinNode child = funcNode.getChild(i);
            if (child instanceof KotlinParser.KtFunctionValueParameters) {
                int paramIdx = 0;
                for (int j = 0; j < child.getNumChildren(); j++) {
                    KotlinNode sub = child.getChild(j);
                    if (sub instanceof KotlinParser.KtFunctionValueParameter && paramIdx < parameters.size()) {
                        String type = parameters.get(paramIdx).getType();
                        if (type != null) {
                            sub.getUserMap().set(KotlinNode.TYPE_NAME_KEY, type);
                        }
                        paramIdx++;
                    }
                }
                break;
            }
        }
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
                // Also set on the parent SingleAnnotation so users can query
                // //SingleAnnotation[@TypeName='org.example.Foo'] directly.
                KotlinNode parent = annNode.getParent();
                if (parent instanceof KotlinParser.KtSingleAnnotation) {
                    parent.getUserMap().set(KotlinNode.TYPE_NAME_KEY, fqn);
                }
            }
        }
    }

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
        } catch (Exception e) {
            LOG.debug("Could not read text region for annotation in {}", annNode, e);
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
