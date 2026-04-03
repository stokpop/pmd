/*
 * BSD-style license; for more info see http://pmd.sourceforge.net/license.html
 */

package net.sourceforge.pmd.lang.kotlin.rule.xpath.internal;

import java.util.List;

import org.checkerframework.checker.nullness.qual.Nullable;

import net.sourceforge.pmd.lang.ast.Node;
import net.sourceforge.pmd.lang.rule.xpath.impl.XPathFunctionDefinition;
import net.sourceforge.pmd.lang.rule.xpath.impl.XPathFunctionException;

import nl.stokpop.typemapper.model.CallSiteAst;
import nl.stokpop.typemapper.model.DeclarationAst;

/**
 * XPath function {@code pmd-kotlin:typeIs(typeName)}.
 *
 * <p>Returns {@code true} when the context node's declared type (for property/variable
 * declarations) or return type (for function declarations) is equivalent to
 * {@code typeName}. Both Java FQCNs and Kotlin FQNs are accepted and treated as
 * equivalent for well-known mapped types (e.g. {@code java.lang.String} ↔
 * {@code kotlin.String}).
 *
 * <p>Requires a pre-analyzed {@link KotlinTypeAnalysisContext} to be set on
 * {@link KotlinTypeAnalysisContextHolder}; returns {@code false} gracefully if
 * no analysis data is available.
 *
 * <p>Example XPath:
 * <pre>{@code
 * //PropertyDeclaration[pmd-kotlin:typeIs('java.util.Calendar')]
 * //FunctionDeclaration[pmd-kotlin:typeIs('kotlin.String')]
 * }</pre>
 */
public final class KotlinTypeIsFunction extends BaseKotlinXPathFunction {

    public static final KotlinTypeIsFunction INSTANCE = new KotlinTypeIsFunction();

    private KotlinTypeIsFunction() {
        super("typeIs");
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
                if (contextNode == null) {
                    return false;
                }
                String typeName = (String) arguments[0];
                String absPath  = contextNode.getTextDocument().getFileId().getAbsolutePath();
                int    line     = contextNode.getBeginLine();

                KotlinTypeAnalysisContext ctx = KotlinTypeAnalysisContextHolder.get();

                // Check declarations at this position (properties → type, functions → returnType)
                List<DeclarationAst> decls = ctx.declarationsAt(absPath, line);
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

                // Fallback: check call-site return type (for expression nodes)
                List<CallSiteAst> calls = ctx.callSitesAt(absPath, line);
                for (CallSiteAst call : calls) {
                    if (ctx.isTypeEquivalent(typeName, call.getReturnType())) {
                        return true;
                    }
                }

                return false;
            }
        };
    }
}
