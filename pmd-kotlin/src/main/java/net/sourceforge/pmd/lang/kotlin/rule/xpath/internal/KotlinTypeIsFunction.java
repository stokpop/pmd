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
 * XPath function {@code pmd-kotlin:typeIs(typeName)}.
 *
 * <p>Returns {@code true} when the context node's declared type is equivalent to, or a
 * <em>subtype</em> of, {@code typeName}. Both Java FQCNs and Kotlin FQNs are accepted.
 *
 * <p>Supported node types:
 * <ul>
 *   <li>{@code PropertyDeclaration} — property / local variable type</li>
 *   <li>{@code FunctionDeclaration} — return type</li>
 *   <li>{@code FunctionValueParameter} — function / constructor parameter type</li>
 *   <li>{@code CatchBlock} — caught exception type</li>
 *   <li>{@code ForStatement} — loop variable type</li>
 * </ul>
 *
 * <p>Subtype checking uses the type hierarchy built by kotlin-type-mapper via reflection.
 * This requires the project's compiled classes (and their dependencies) to be on the
 * {@code auxClasspath}. Without classpath, the function falls back to exact-name matching.
 *
 * <p>For exact-type matching only, use {@code pmd-kotlin:typeIsExactly(typeName)}.
 *
 * <p>Example XPath:
 * <pre>{@code
 * //PropertyDeclaration[pmd-kotlin:typeIs('java.io.Serializable')]          -- matches subtypes too
 * //FunctionDeclaration[pmd-kotlin:typeIs('kotlin.String')]                  -- return type
 * //FunctionValueParameter[pmd-kotlin:typeIs('java.util.Map')]               -- parameter type
 * //CatchBlock[pmd-kotlin:typeIs('java.io.IOException')]                     -- exception type
 * //ForStatement[pmd-kotlin:typeIs('kotlin.String')]                         -- loop variable type
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

                // Fast path: use node attributes set by KotlinLanguageProcessor annotation pass.
                // These are populated automatically when running via PMD CLI or Designer.
                if (contextNode instanceof KotlinNode) {
                    KotlinNode kn = (KotlinNode) contextNode;
                    KotlinTypeAnalysisContext nodeCtx = KotlinTypeAnalysisContextHolder.get();
                    String nodeType = kn.getTypeName();
                    if (nodeType != null) {
                        return nodeCtx.isSubtypeOf(typeName, nodeType);
                    }
                    String nodeReturnType = kn.getReturnTypeName();
                    if (nodeReturnType != null) {
                        return nodeCtx.isSubtypeOf(typeName, nodeReturnType);
                    }
                }

                KotlinTypeAnalysisContext ctx = KotlinTypeAnalysisContextHolder.get();

                // Check declarations at this position (properties → type, functions → returnType)
                List<DeclarationAst> decls = ctx.declarationsAt(absPath, line);
                for (DeclarationAst decl : decls) {
                    String type = decl.getType();
                    if (type != null && ctx.isSubtypeOf(typeName, type)) {
                        return true;
                    }
                    String returnType = decl.getReturnType();
                    if (returnType != null && ctx.isSubtypeOf(typeName, returnType)) {
                        return true;
                    }
                }

                // Fallback: check call-site return type (for expression nodes)
                List<CallSiteAst> calls = ctx.callSitesAt(absPath, line);
                for (CallSiteAst call : calls) {
                    if (ctx.isSubtypeOf(typeName, call.getReturnType())) {
                        return true;
                    }
                }

                return false;
            }
        };
    }
}
