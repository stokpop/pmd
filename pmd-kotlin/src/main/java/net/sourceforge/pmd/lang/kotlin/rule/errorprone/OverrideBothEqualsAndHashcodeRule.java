/*
 * BSD-style license; for more info see http://pmd.sourceforge.net/license.html
 */


package net.sourceforge.pmd.lang.kotlin.rule.errorprone;

import java.util.List;

import org.checkerframework.checker.nullness.qual.NonNull;

import net.sourceforge.pmd.lang.kotlin.AbstractKotlinRule;
import net.sourceforge.pmd.lang.kotlin.ast.KotlinParser.KtClassBody;
import net.sourceforge.pmd.lang.kotlin.ast.KotlinParser.KtClassDeclaration;
import net.sourceforge.pmd.lang.kotlin.ast.KotlinParser.KtFunctionDeclaration;
import net.sourceforge.pmd.lang.kotlin.ast.KotlinParser.KtSimpleIdentifier;
import net.sourceforge.pmd.lang.kotlin.ast.KotlinParser.KtTopLevelObject;
import net.sourceforge.pmd.lang.kotlin.ast.KotlinTerminalNode;
import net.sourceforge.pmd.lang.kotlin.ast.KotlinVisitor;
import net.sourceforge.pmd.lang.kotlin.ast.KotlinVisitorBase;
import net.sourceforge.pmd.lang.rule.RuleTargetSelector;
import net.sourceforge.pmd.reporting.RuleContext;

public class OverrideBothEqualsAndHashcodeRule extends AbstractKotlinRule {

    private static final Visitor INSTANCE = new Visitor();

    @Override
    public KotlinVisitor<RuleContext, ?> buildVisitor() {
        return INSTANCE;
    }

    @Override
    protected @NonNull RuleTargetSelector buildTargetSelector() {
        return RuleTargetSelector.forTypes(KtTopLevelObject.class);
    }

    private static final class Visitor extends KotlinVisitorBase<RuleContext, Void> {
        @Override
        public Void visitClassBody(KtClassBody node, RuleContext data) {
            List<KtFunctionDeclaration> functions = node.descendants(KtFunctionDeclaration.class).toList();

            boolean hasEqualMethod = functions.stream().filter(this::isEqualsMethod).count() == 1L;
            boolean hasHashCodeMethod = functions.stream().filter(this::isHashCodeMethod).count() == 1L;

            if (hasEqualMethod ^ hasHashCodeMethod) {
                data.addViolation(node.ancestors(KtClassDeclaration.class).first());
            }

            return super.visitClassBody(node, data);
        }

        private boolean isEqualsMethod(KtFunctionDeclaration fun) {
            String name = getFunctionName(fun);
            int arity = getArity(fun);
            return "equals".equals(name) && hasOverrideModifier(fun) && arity == 1;
        }

        private boolean isHashCodeMethod(KtFunctionDeclaration fun) {
            String name = getFunctionName(fun);
            int arity = getArity(fun);
            return "hashCode".equals(name) && hasOverrideModifier(fun) && arity == 0;
        }

        private String getFunctionName(KtFunctionDeclaration fun) {
            // `simpleIdentifier()` returns a java.util.List<KtSimpleIdentifier>, which does not have getFirst().
            // Safely get the first identifier if present.
            List<KtSimpleIdentifier> ids = fun.identifier().simpleIdentifier();
            if (ids == null || ids.isEmpty()) {
                return "";
            }
            KotlinTerminalNode term = ids.get(0).descendants(KotlinTerminalNode.class).first();
            if (term == null) {
                return "";
            }
            return term.getText();
        }

        private boolean hasOverrideModifier(KtFunctionDeclaration fun) {
            return fun.functionModifierList().descendants(KotlinTerminalNode.class)
                    .any(t -> "override".equals(t.getText()));
        }

        private int getArity(KtFunctionDeclaration fun) {
            return fun.functionValueParameters().functionValueParameter().size();
        }
    }
}
