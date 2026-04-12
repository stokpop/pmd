/*
 * BSD-style license; for more info see http://pmd.sourceforge.net/license.html
 */

package net.sourceforge.pmd.lang.kotlin.rule.xpath.internal;

import org.checkerframework.checker.nullness.qual.Nullable;

import net.sourceforge.pmd.lang.ast.Node;
import net.sourceforge.pmd.lang.kotlin.ast.KotlinNode;
import net.sourceforge.pmd.lang.kotlin.ast.KotlinParser;
import net.sourceforge.pmd.lang.rule.xpath.impl.XPathFunctionException;

/**
 * XPath function {@code pmd-kotlin:hasAnnotation(className)}.
 *
 * <p>Returns {@code true} when the context node carries an annotation whose name
 * matches {@code className}.  The function is useful on declaration nodes such as
 * {@code functionDeclaration}, {@code propertyDeclaration}, {@code classDeclaration},
 * and {@code objectDeclaration}.
 *
 * <p><b>Name matching rules</b> (checked in order):
 * <ol>
 *   <li>If kotlin-type-mapper resolved the annotation FQN, the {@code @TypeName}
 *       attribute on child {@code UnescapedAnnotation} nodes is used for an exact
 *       FQN or simple-name match.</li>
 *   <li>Falls back to {@link KotlinNode#ANNOTATION_NAMES_KEY} on the declaration node.</li>
 *   <li>Falls back to the annotation name <em>as written in source</em> (via text region),
 *       which always works for simple names regardless of whether type resolution ran.</li>
 * </ol>
 *
 * <p>Both a fully-qualified name (e.g. {@code 'javax.persistence.Column'}) and a
 * simple name (e.g. {@code 'Column'}) are accepted. Without FQN resolution, a query
 * for {@code 'javax.persistence.Column'} still matches {@code @Column} because the
 * simple-name suffix is compared.
 *
 * <p>Example XPath:
 * <pre>{@code
 * //functionDeclaration[pmd-kotlin:hasAnnotation('Deprecated')]
 * //propertyDeclaration[pmd-kotlin:hasAnnotation('javax.persistence.Column')]
 * //classDeclaration[pmd-kotlin:hasAnnotation('org.springframework.stereotype.Service')]
 * }</pre>
 */
public final class KotlinHasAnnotationFunction extends BaseKotlinXPathFunction {

    public static final KotlinHasAnnotationFunction INSTANCE = new KotlinHasAnnotationFunction();

    private KotlinHasAnnotationFunction() {
        super("hasAnnotation");
    }

    @Override
    public Type[] getArgumentTypes() {
        return new Type[]{Type.SINGLE_STRING};
    }

    @Override
    public Type getResultType() {
        return Type.SINGLE_BOOLEAN;
    }

    @Override
    public boolean dependsOnContext() {
        return true;
    }

    @Override
    public FunctionCall makeCallExpression() {
        return new FunctionCall() {
            @Override
            public Object call(@Nullable Node contextNode, Object[] arguments) throws XPathFunctionException {
                if (!(contextNode instanceof KotlinNode)) {
                    return false;
                }
                String className = (String) arguments[0];
                String simpleName = simpleNameOf(className);

                KotlinNode declNode = (KotlinNode) contextNode;

                // Path 1 — @TypeName on UnescapedAnnotation children (set by
                // KotlinTypeAnnotationVisitor when kotlin-type-mapper ran and resolved FQNs)
                if (checkAnnotationChildrenByTypeName(declNode, className, simpleName)) {
                    return true;
                }

                // Path 2 — ANNOTATION_NAMES_KEY comma list on the declaration node
                for (String fqn : declNode.getAnnotationFqNames()) {
                    if (fqn.equals(className) || simpleNameOf(fqn).equals(simpleName)) {
                        return true;
                    }
                }

                // Path 3 — source-text fallback: read the annotation name as written
                // in source via text region. Only used when className is a simple (unqualified)
                // name — if a FQN is given, FQN resolution via paths 1/2 is required.
                if (!className.contains(".") && checkAnnotationChildrenBySourceText(declNode, className, simpleName)) {
                    return true;
                }

                return false;
            }
        };
    }

    /**
     * Walks children of {@code declNode} (stopping at body boundaries) looking for
     * {@code UnescapedAnnotation} nodes whose {@code @TypeName} (FQN set by
     * kotlin-type-mapper) matches {@code className} or its simple-name suffix.
     */
    private static boolean checkAnnotationChildrenByTypeName(
            KotlinNode declNode, String className, String simpleName) {
        for (int i = 0; i < declNode.getNumChildren(); i++) {
            KotlinNode child = declNode.getChild(i);
            if (annotationNodeMatchesByTypeName(child, className, simpleName)) {
                return true;
            }
        }
        return false;
    }

    private static boolean annotationNodeMatchesByTypeName(
            KotlinNode node, String className, String simpleName) {
        String xpathName = node.getXPathNodeName();
        if ("FunctionBody".equals(xpathName) || "ClassBody".equals(xpathName)
                || "Block".equals(xpathName)) {
            return false;
        }
        if ("UnescapedAnnotation".equals(xpathName)) {
            String typeName = node.getUserMap().get(KotlinNode.TYPE_NAME_KEY);
            if (typeName != null) {
                return typeName.equals(className) || simpleNameOf(typeName).equals(simpleName);
            }
            return false; // no FQN resolved for this annotation
        }
        for (int i = 0; i < node.getNumChildren(); i++) {
            if (annotationNodeMatchesByTypeName(node.getChild(i), className, simpleName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Walks children of {@code declNode} (stopping at body boundaries) looking for
     * {@code UnescapedAnnotation} nodes and reads the annotation name as written in
     * source using the text region. Matches on exact text, simple-name suffix, or
     * the simple-name of the written text matching the simple-name of {@code className}.
     */
    private static boolean checkAnnotationChildrenBySourceText(
            KotlinNode declNode, String className, String simpleName) {
        for (int i = 0; i < declNode.getNumChildren(); i++) {
            KotlinNode child = declNode.getChild(i);
            if (annotationNodeMatchesBySourceText(child, className, simpleName)) {
                return true;
            }
        }
        return false;
    }

    private static boolean annotationNodeMatchesBySourceText(
            KotlinNode node, String className, String simpleName) {
        String xpathName = node.getXPathNodeName();
        if ("FunctionBody".equals(xpathName) || "ClassBody".equals(xpathName)
                || "Block".equals(xpathName)) {
            return false;
        }
        if ("UnescapedAnnotation".equals(xpathName)) {
            String writtenName = getAnnotationSourceText((KotlinParser.KtUnescapedAnnotation) node);
            if (writtenName != null) {
                // exact match (e.g. written as FQCN), or simple-name cross-match
                if (writtenName.equals(className)
                        || simpleNameOf(writtenName).equals(simpleName)) {
                    return true;
                }
            }
            return false;
        }
        for (int i = 0; i < node.getNumChildren(); i++) {
            if (annotationNodeMatchesBySourceText(node.getChild(i), className, simpleName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the annotation type name as written in source (e.g. {@code "Column"}
     * or {@code "javax.persistence.Column"}) from the {@code KtUserType} inside the
     * given {@code UnescapedAnnotation} node.  Returns {@code null} on failure.
     */
    static @Nullable String getAnnotationSourceText(KotlinParser.KtUnescapedAnnotation annNode) {
        // unescapedAnnotation : constructorInvocation | userType
        // constructorInvocation : userType valueArguments
        for (int i = 0; i < annNode.getNumChildren(); i++) {
            KotlinNode child = annNode.getChild(i);
            KotlinParser.KtUserType userType = null;
            if (child instanceof KotlinParser.KtUserType) {
                userType = (KotlinParser.KtUserType) child;
            } else if (child instanceof KotlinParser.KtConstructorInvocation) {
                for (int j = 0; j < child.getNumChildren(); j++) {
                    if (child.getChild(j) instanceof KotlinParser.KtUserType) {
                        userType = (KotlinParser.KtUserType) child.getChild(j);
                        break;
                    }
                }
            }
            if (userType != null) {
                try {
                    return userType.getTextDocument()
                            .sliceOriginalText(userType.getTextRegion())
                            .toString();
                } catch (Exception ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    /** Returns the last dot-separated segment, e.g. {@code "Column"} from {@code "javax.persistence.Column"}. */
    static String simpleNameOf(String name) {
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1) : name;
    }
}
