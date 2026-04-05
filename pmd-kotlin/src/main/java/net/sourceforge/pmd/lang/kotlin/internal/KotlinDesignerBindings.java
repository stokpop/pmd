/*
 * BSD-style license; for more info see http://pmd.sourceforge.net/license.html
 */

package net.sourceforge.pmd.lang.kotlin.internal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import net.sourceforge.pmd.lang.ast.Node;
import net.sourceforge.pmd.lang.kotlin.ast.KotlinNode;
import net.sourceforge.pmd.util.designerbindings.DesignerBindings.DefaultDesignerBindings;

/**
 * Designer bindings for Kotlin. Populates the "Additional info" section
 * of the PMD Designer with resolved type names and modifiers — mirroring
 * what {@code JavaDesignerBindings} does for Java nodes.
 */
public final class KotlinDesignerBindings extends DefaultDesignerBindings {

    public static final KotlinDesignerBindings INSTANCE = new KotlinDesignerBindings();

    private KotlinDesignerBindings() {
    }

    @Override
    public Collection<AdditionalInfo> getAdditionalInfo(Node node) {
        List<AdditionalInfo> info = new ArrayList<>(super.getAdditionalInfo(node));
        if (!(node instanceof KotlinNode)) {
            return info;
        }
        KotlinNode kNode = (KotlinNode) node;

        // Modifiers — shown as "pmd-kotlin:modifiers(): (override, suspend)"
        String mods = kNode.getModifiers();
        if (mods != null) {
            String formatted = Arrays.stream(mods.split(" "))
                    .collect(Collectors.joining(", ", "(", ")"));
            info.add(new AdditionalInfo("pmd-kotlin:modifiers(): " + formatted));
        }

        // TypeName — shown as "TypeName: org.example.Service"
        String typeName = kNode.getTypeName();
        if (typeName != null) {
            info.add(new AdditionalInfo("TypeName: " + typeName));
        }

        // ReturnTypeName — shown as "ReturnTypeName: kotlin.String"
        String returnTypeName = kNode.getReturnTypeName();
        if (returnTypeName != null) {
            info.add(new AdditionalInfo("ReturnTypeName: " + returnTypeName));
        }

        return info;
    }
}
