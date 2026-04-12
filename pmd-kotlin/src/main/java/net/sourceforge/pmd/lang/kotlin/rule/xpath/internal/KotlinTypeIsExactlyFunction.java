/*
 * BSD-style license; for more info see http://pmd.sourceforge.net/license.html
 */

package net.sourceforge.pmd.lang.kotlin.rule.xpath.internal;

import java.util.List;

import org.checkerframework.checker.nullness.qual.Nullable;

import net.sourceforge.pmd.lang.ast.Node;
import net.sourceforge.pmd.lang.kotlin.ast.KotlinNode;
import net.sourceforge.pmd.lang.rule.xpath.impl.XPathFunctionDefinition;
import net.sourceforge.pmd.lang.rule.xpath.impl.XPathFunctionException;

import nl.stokpop.typemapper.model.CallSiteAst;
import nl.stokpop.typemapper.model.DeclarationAst;

/**
 * XPath function {@code pmd-kotlin:typeIsExactly(typeName)}.
 *
 * <p>Returns {@code true} when the context node's declared type (for property/variable
 * declarations) or return type (for function declarations) is <em>exactly</em> equivalent
 * to {@code typeName} — no subtype checking is performed.
 * Both Java FQCNs and Kotlin FQNs are accepted (e.g. {@code java.lang.String} ↔
 * {@code kotlin.String}).
 *
 * <p>Use {@code pmd-kotlin:typeIs(typeName)} when subtype matches should also be included.
 *
 * <p>Example XPath:
 * <pre>{@code
 * //PropertyDeclaration[pmd-kotlin:typeIsExactly('java.util.Calendar')]
 * //FunctionDeclaration[pmd-kotlin:typeIsExactly('kotlin.String')]
 * }</pre>
 */
public final class KotlinTypeIsExactlyFunction extends BaseKotlinXPathFunction {

    public static final KotlinTypeIsExactlyFunction INSTANCE = new KotlinTypeIsExactlyFunction();

    private KotlinTypeIsExactlyFunction() {
        super("typeIsExactly");
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
        return new TypeIsExactlyFunctionCall();
    }

    private static final class TypeIsExactlyFunctionCall implements FunctionCall {

        @Override
        public Object call(@Nullable Node contextNode, Object[] arguments) throws XPathFunctionException {
            if (contextNode == null) {
                return false;
            }
            String typeName = (String) arguments[0];
            KotlinTypeAnalysisContext ctx = KotlinTypeAnalysisContextHolder.get();

            // Fast path: use node attributes set by the annotation pass.
            if (contextNode instanceof KotlinNode
                    && matchesNodeAttribute((KotlinNode) contextNode, typeName, ctx)) {
                return true;
            }

            String absPath = contextNode.getTextDocument().getFileId().getAbsolutePath();
            int line = contextNode.getBeginLine();

            return matchesAnyDeclaration(ctx.declarationsAt(absPath, line), typeName, ctx)
                    || matchesAnyCallSite(ctx.callSitesAt(absPath, line), typeName, ctx);
        }

        private static boolean matchesNodeAttribute(
                KotlinNode node, String typeName, KotlinTypeAnalysisContext ctx) {
            String nodeType = node.getTypeName();
            if (nodeType != null) {
                return ctx.isTypeEquivalent(typeName, nodeType);
            }
            String returnType = node.getReturnTypeName();
            if (returnType != null) {
                return ctx.isTypeEquivalent(typeName, returnType);
            }
            return false;
        }

        private static boolean matchesAnyDeclaration(
                List<DeclarationAst> decls, String typeName, KotlinTypeAnalysisContext ctx) {
            for (DeclarationAst decl : decls) {
                String type = decl.getType();
                if (type != null && ctx.isTypeEquivalent(typeName, type)) {
                    return true;
                }
                String returnType = decl.getReturnType();
                if (returnType != null && ctx.isTypeEquivalent(typeName, returnType)) {
                    return true;
                }
            }
            return false;
        }

        private static boolean matchesAnyCallSite(
                List<CallSiteAst> calls, String typeName, KotlinTypeAnalysisContext ctx) {
            for (CallSiteAst call : calls) {
                if (ctx.isTypeEquivalent(typeName, call.getReturnType())) {
                    return true;
                }
            }
            return false;
        }
    }
}
