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
 * <p><b>How type resolution works:</b> Before any rules are evaluated, the Kotlin K1 compiler
 * (via kotlin-type-mapper) analyzes all source files using the aux classpath jars to resolve types
 * and record call site signatures into {@link KotlinTypeAnalysisContext}. At rule evaluation time,
 * {@code matchesSig} only queries that pre-computed data — no jars are needed then.
 * If a required jar is missing from the classpath, the type will be unresolved and the
 * {@code UnresolvedType} rule will fire as a signal, while {@code matchesSig} returns {@code false}.
 *
 * <p><b>Multi-line chain support:</b> When a {@code PostfixUnaryExpression} spans multiple lines
 * (e.g. a method call split across lines), all call sites in the full line range are checked,
 * so {@code matchesSig} works correctly for chained calls like:
 * <pre>{@code
 * val expr = xpath
 *     .compile("//book") // call on line N+1 — correctly matched
 * }</pre>
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
        return new MatchesSigFunctionCall();
    }

    private static final class MatchesSigFunctionCall implements FunctionCall {
        @Override
        public void staticInit(Object[] arguments) throws XPathFunctionException {
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
            String sig      = (String) arguments[0];
            String absPath  = contextNode.getTextDocument().getFileId().getAbsolutePath();
            int beginLine   = contextNode.getBeginLine();
            int endLine     = contextNode.getEndLine();
            int beginCol    = contextNode.getBeginColumn();
            int endCol      = contextNode.getEndColumn();
            boolean singleLine = endLine == beginLine;

            KotlinTypeAnalysisContext ctx = KotlinTypeAnalysisContextHolder.get();
            List<CallSiteAst> sites = ctx.callSitesInRange(absPath, beginLine, endLine);
            for (CallSiteAst call : sites) {
                boolean callSiteMatch = matchesCallSite(call, beginLine, beginCol, endCol, singleLine);
                boolean sigMatch = callSiteMatch && SignatureMatcherKt.matchesSigPolymorphic(call, sig, ctx::isSubtypeOf);
                if (sigMatch) {
                    return true;
                }
            }
            return false;
        }

        private static boolean matchesCallSite(CallSiteAst call,
                                               int beginLine, int beginCol, int endCol,
                                               boolean singleLine) {
            if (singleLine) {
                // Single-line: filter by column range to distinguish multiple calls on the same line
                int col = call.getColumn();
                return col >= beginCol && col <= endCol;
            }
            // First line of multi-line expression: call must start at or after the expression start
            return call.getLine() != beginLine || call.getColumn() >= beginCol;
        }
    }
}
