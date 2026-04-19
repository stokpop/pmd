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

abstract class AbstractKotlinTypeIsFunctionCall implements XPathFunctionDefinition.FunctionCall {

    @Override
    public final Object call(@Nullable Node contextNode, Object[] arguments) throws XPathFunctionException {
        if (contextNode == null) {
            return false;
        }

        String typeName = (String) arguments[0];
        KotlinTypeAnalysisContext ctx = KotlinTypeAnalysisContextHolder.get();

        if (contextNode instanceof KotlinNode
                && matchesNodeAttribute((KotlinNode) contextNode, typeName, ctx)) {
            return true;
        }

        String absPath = contextNode.getTextDocument().getFileId().getAbsolutePath();
        int line = contextNode.getBeginLine();
        List<DeclarationAst> decls = ctx.declarationsAt(absPath, line);
        if (!decls.isEmpty()) {
            return matchesAnyDeclaration(decls, typeName, ctx);
        }
        return matchesAnyCallSite(ctx.callSitesAt(absPath, line), typeName, ctx);
    }

    protected abstract boolean matchesNodeAttribute(KotlinNode node, String typeName, KotlinTypeAnalysisContext ctx);

    protected abstract boolean matchesType(String expectedType, String actualType, KotlinTypeAnalysisContext ctx);

    private boolean matchesAnyDeclaration(List<DeclarationAst> decls, String typeName, KotlinTypeAnalysisContext ctx) {
        for (DeclarationAst decl : decls) {
            String type = decl.getType();
            if (type != null && matchesType(typeName, type, ctx)) {
                return true;
            }
            String returnType = decl.getReturnType();
            if (returnType != null && matchesType(typeName, returnType, ctx)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesAnyCallSite(List<CallSiteAst> calls, String typeName, KotlinTypeAnalysisContext ctx) {
        for (CallSiteAst call : calls) {
            if (matchesType(typeName, call.getReturnType(), ctx)) {
                return true;
            }
        }
        return false;
    }
}
