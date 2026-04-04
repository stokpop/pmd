/*
 * BSD-style license; for more info see http://pmd.sourceforge.net/license.html
 */

package net.sourceforge.pmd.lang.kotlin.ast;

import org.checkerframework.checker.nullness.qual.Nullable;

import net.sourceforge.pmd.lang.ast.impl.antlr4.AntlrNode;
import net.sourceforge.pmd.util.DataMap;
import net.sourceforge.pmd.util.DataMap.SimpleDataKey;

/**
 * Supertype of all kotlin nodes.
 */
public interface KotlinNode extends AntlrNode<KotlinNode> {

    /**
     * DataMap key for the resolved type of a {@code PropertyDeclaration} node,
     * e.g. {@code "java.util.Calendar"} or {@code "kotlin.String"}.
     * Populated by the type annotation pass when kotlin-type-mapper analysis is available.
     */
    SimpleDataKey<String> TYPE_NAME_KEY = DataMap.simpleDataKey("kotlin.typeName");

    /**
     * DataMap key for the resolved return type of a {@code FunctionDeclaration} node,
     * e.g. {@code "java.util.Calendar"} or {@code "kotlin.String"}.
     * Populated by the type annotation pass when kotlin-type-mapper analysis is available.
     */
    SimpleDataKey<String> RETURN_TYPE_KEY = DataMap.simpleDataKey("kotlin.returnTypeName");

    /**
     * Returns the resolved type name for a {@code PropertyDeclaration} node, or {@code null}
     * if type analysis has not been run. Exposed as XPath attribute {@code @TypeName}.
     */
    default @Nullable String getTypeName() {
        return getUserMap().get(TYPE_NAME_KEY);
    }

    /**
     * Returns the resolved return type name for a {@code FunctionDeclaration} node, or
     * {@code null} if type analysis has not been run. Exposed as XPath attribute
     * {@code @ReturnTypeName}.
     */
    default @Nullable String getReturnTypeName() {
        return getUserMap().get(RETURN_TYPE_KEY);
    }
}
