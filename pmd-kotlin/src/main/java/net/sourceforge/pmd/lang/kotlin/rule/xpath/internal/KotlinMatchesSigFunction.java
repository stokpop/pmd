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
import nl.stokpop.typemapper.model.SignatureMatcherKt;

/**
 * XPath function {@code pmd-kotlin:matchesSig(sig)}.
 *
 * <p>Returns {@code true} when the context node corresponds to a call site whose
 * signature matches {@code sig}. The signature format mirrors PMD Java's
 * {@code matchesSig}, but both Java FQCNs and Kotlin FQNs are accepted for
 * receiver and parameter types (e.g. {@code java.lang.String} ↔ {@code kotlin.String}).
 *
 * <p>Signature format: {@code [receiverType#]methodName(paramType,...)}
 * <ul>
 *   <li>{@code _} wildcard accepted for receiver and each parameter type</li>
 *   <li>{@code *} accepts any parameter list</li>
 *   <li>{@code <init>} matches constructors</li>
 * </ul>
 *
 * <p>Requires a pre-analyzed {@link KotlinTypeAnalysisContext}; returns {@code false}
 * gracefully if no analysis data is available.
 *
 * <p>Example XPath:
 * <pre>{@code
 * //PostfixUnaryExpression[pmd-kotlin:matchesSig('java.util.regex.Pattern#matches(java.lang.String,java.lang.CharSequence)')]
 * //PostfixUnaryExpression[pmd-kotlin:matchesSig('java.util.regex.Pattern#compile(_)')]
 * }</pre>
 */
public final class KotlinMatchesSigFunction extends BaseKotlinXPathFunction {

    public static final KotlinMatchesSigFunction INSTANCE = new KotlinMatchesSigFunction();

    private KotlinMatchesSigFunction() {
        super("matchesSig");
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
            public void staticInit(Object[] arguments) throws XPathFunctionException {
                // Validate the signature at expression-build time so users get an early error.
                try {
                    SignatureMatcherKt.parseSig((String) arguments[0]);
                } catch (IllegalArgumentException e) {
                    throw new XPathFunctionException(
                            "Invalid matchesSig argument: " + e.getMessage(), e);
                }
            }

            @Override
            public Object call(@Nullable Node contextNode, Object[] arguments) throws XPathFunctionException {
                if (contextNode == null) {
                    return false;
                }
                String sig     = (String) arguments[0];
                String absPath = contextNode.getTextDocument().getFileId().getAbsolutePath();
                int    line    = contextNode.getBeginLine();

                KotlinTypeAnalysisContext ctx = KotlinTypeAnalysisContextHolder.get();
                List<CallSiteAst> calls = ctx.callSitesAt(absPath, line);
                for (CallSiteAst call : calls) {
                    if (SignatureMatcherKt.matchesSigEquivalent(call, sig)) {
                        return true;
                    }
                }
                return false;
            }
        };
    }
}
