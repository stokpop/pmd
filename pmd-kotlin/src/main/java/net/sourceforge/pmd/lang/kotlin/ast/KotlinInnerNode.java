/*
 * BSD-style license; for more info see http://pmd.sourceforge.net/license.html
 */

package net.sourceforge.pmd.lang.kotlin.ast;

import java.util.Iterator;
import java.util.NoSuchElementException;

import org.antlr.v4.runtime.ParserRuleContext;

import net.sourceforge.pmd.lang.ast.AstVisitor;
import net.sourceforge.pmd.lang.ast.impl.antlr4.BaseAntlrInnerNode;
import net.sourceforge.pmd.lang.rule.xpath.Attribute;

abstract class KotlinInnerNode extends BaseAntlrInnerNode<KotlinNode> implements KotlinNode {

    KotlinInnerNode(ParserRuleContext parent, int invokingStateNumber) {
        super(parent, invokingStateNumber);
    }

    @Override
    public <P, R> R acceptVisitor(AstVisitor<? super P, ? extends R> visitor, P data) {
        if (visitor instanceof KotlinVisitor) {
            // some of the generated antlr nodes have no accept method...
            return ((KotlinVisitor<? super P, ? extends R>) visitor).visitKotlinNode(this, data);
        }
        return visitor.visitNode(this, data);
    }


    @Override // override to make visible in package
    protected PmdAsAntlrInnerNode<KotlinNode> asAntlrNode() {
        return super.asAntlrNode();
    }

    @Override
    public String getXPathNodeName() {
        return KotlinParser.DICO.getXPathNameOfRule(getRuleIndex());
    }

    /**
     * Overridden to suppress attributes whose getter returns {@code null}.
     * This prevents optional attributes like {@code @TypeName} and
     * {@code @ReturnTypeName} from appearing on every node in the PMD Designer
     * when they have no meaningful value — consistent with how {@code @Text}
     * is only present on terminal (T-prefixed) nodes.
     */
    @Override
    public Iterator<Attribute> getXPathAttributesIterator() {
        Iterator<Attribute> base = super.getXPathAttributesIterator();
        return new Iterator<Attribute>() {
            private Attribute pending;

            {
                advance();
            }

            private void advance() {
                pending = null;
                while (base.hasNext()) {
                    Attribute attr = base.next();
                    if (attr.getValue() != null) {
                        pending = attr;
                        break;
                    }
                }
            }

            @Override
            public boolean hasNext() {
                return pending != null;
            }

            @Override
            public Attribute next() {
                if (pending == null) {
                    throw new NoSuchElementException();
                }
                Attribute result = pending;
                advance();
                return result;
            }
        };
    }
}
