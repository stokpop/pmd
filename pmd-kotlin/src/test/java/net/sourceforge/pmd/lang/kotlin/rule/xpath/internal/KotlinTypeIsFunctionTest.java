/*
 * BSD-style license; for more info see http://pmd.sourceforge.net/license.html
 */

package net.sourceforge.pmd.lang.kotlin.rule.xpath.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.net.URL;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.sourceforge.pmd.PMDConfiguration;
import net.sourceforge.pmd.PmdAnalysis;
import net.sourceforge.pmd.lang.LanguageRegistry;
import net.sourceforge.pmd.lang.rule.Rule;
import net.sourceforge.pmd.lang.rule.RuleSet;
import net.sourceforge.pmd.lang.rule.xpath.XPathRule;
import net.sourceforge.pmd.lang.rule.xpath.XPathVersion;
import net.sourceforge.pmd.reporting.Report;

class KotlinTypeIsFunctionTest {

    private static final String TYPE_IS_RESOURCE_DIR =
            "net/sourceforge/pmd/lang/kotlin/rule/xpath/typeIs";

    private KotlinTypeXPathTestHelper helper;

    @BeforeEach
    void setUp() {
        URL resource = getClass().getClassLoader().getResource(TYPE_IS_RESOURCE_DIR);
        if (resource == null) {
            throw new IllegalStateException("Cannot find test resources at: " + TYPE_IS_RESOURCE_DIR);
        }
        helper = KotlinTypeXPathTestHelper.forDirectory(new File(resource.getFile()));
        helper.injectContext();
    }

    @AfterEach
    void tearDown() {
        KotlinTypeAnalysisContextHolder.clearGlobal();
    }

    @Test
    void typeIsCalendarMatchesMeetingProperty() {
        File kotlinFile = getResource(TYPE_IS_RESOURCE_DIR + "/CalendarUsage.kt");
        Report report = runXPath("//PropertyDeclaration[pmd-kotlin:typeIs('java.util.Calendar')]", kotlinFile);
        assertTrue(report.getProcessingErrors().isEmpty(), "No processing errors expected");
        // 'meeting' property should match
        assertTrue(report.getViolations().stream().anyMatch(v -> v.getBeginLine() == 6),
                "Expected violation at line 6 (meeting property)");
    }

    @Test
    void typeIsCalendarMatchesGetDeadlineFunction() {
        File kotlinFile = getResource(TYPE_IS_RESOURCE_DIR + "/CalendarUsage.kt");
        Report report = runXPath("//FunctionDeclaration[pmd-kotlin:typeIs('java.util.Calendar')]", kotlinFile);
        assertTrue(report.getProcessingErrors().isEmpty(), "No processing errors expected");
        // 'getDeadline' function should match
        assertTrue(report.getViolations().stream().anyMatch(v -> v.getBeginLine() == 7),
                "Expected violation at line 7 (getDeadline function)");
    }

    @Test
    void typeIsCalendarDoesNotMatchNameProperty() {
        File kotlinFile = getResource(TYPE_IS_RESOURCE_DIR + "/CalendarUsage.kt");
        Report report = runXPath("//PropertyDeclaration[pmd-kotlin:typeIs('java.util.Calendar')]", kotlinFile);
        assertTrue(report.getProcessingErrors().isEmpty(), "No processing errors expected");
        // 'name' property (line 10) should NOT match
        assertTrue(report.getViolations().stream().noneMatch(v -> v.getBeginLine() == 10),
                "Did not expect violation at line 10 (name: String property)");
    }

    @Test
    void typeIsKotlinStringMatchesMessageProperty() {
        File kotlinFile = getResource(TYPE_IS_RESOURCE_DIR + "/StringEquivalence.kt");
        Report report = runXPath("//PropertyDeclaration[pmd-kotlin:typeIs('kotlin.String')]", kotlinFile);
        assertTrue(report.getProcessingErrors().isEmpty(), "No processing errors expected");
        assertTrue(report.getViolations().stream().anyMatch(v -> v.getBeginLine() == 3),
                "Expected violation at line 3 (message property)");
    }

    @Test
    void typeIsJavaLangStringMatchesMessageProperty() {
        File kotlinFile = getResource(TYPE_IS_RESOURCE_DIR + "/StringEquivalence.kt");
        Report report = runXPath("//PropertyDeclaration[pmd-kotlin:typeIs('java.lang.String')]", kotlinFile);
        assertTrue(report.getProcessingErrors().isEmpty(), "No processing errors expected");
        // Java name should be mapped to Kotlin String and match
        assertTrue(report.getViolations().stream().anyMatch(v -> v.getBeginLine() == 3),
                "Expected violation at line 3 (message property) using java.lang.String name");
    }

    @Test
    void typeIsKotlinStringMatchesGreetFunction() {
        File kotlinFile = getResource(TYPE_IS_RESOURCE_DIR + "/StringEquivalence.kt");
        Report report = runXPath("//FunctionDeclaration[pmd-kotlin:typeIs('kotlin.String')]", kotlinFile);
        assertTrue(report.getProcessingErrors().isEmpty(), "No processing errors expected");
        assertTrue(report.getViolations().stream().anyMatch(v -> v.getBeginLine() == 4),
                "Expected violation at line 4 (greet function)");
    }

    @Test
    void typeIsListDoesNotMatchCalendarProperty() {
        File kotlinFile = getResource(TYPE_IS_RESOURCE_DIR + "/CalendarUsage.kt");
        Report report = runXPath("//PropertyDeclaration[pmd-kotlin:typeIs('kotlin.collections.List')]", kotlinFile);
        assertTrue(report.getProcessingErrors().isEmpty(), "No processing errors expected");
        assertEquals(0, report.getViolations().size(),
                "Expected no violations for List typeIs on CalendarUsage.kt");
    }

    @Test
    void typeIsSerializableMatchesSubtypePropertyViaHierarchy() {
        // typeIs should match a property whose declared type implements Serializable,
        // using the type hierarchy from kotlin-type-mapper.
        // Note: this requires the compiled classes on auxClasspath to resolve the hierarchy;
        // without classpath the type hierarchy is empty and the test is skipped gracefully.
        File kotlinFile = getResource(TYPE_IS_RESOURCE_DIR + "/SerializableSubtype.kt");
        KotlinTypeAnalysisContext ctx = KotlinTypeAnalysisContextHolder.get();
        if (ctx.getTypeHierarchy().isEmpty()) {
            // No hierarchy available (no compiled classes on classpath) — skip
            return;
        }
        Report report = runXPath("//PropertyDeclaration[pmd-kotlin:typeIs('java.io.Serializable')]", kotlinFile);
        assertTrue(report.getProcessingErrors().isEmpty(), "No processing errors expected");
        assertTrue(report.getViolations().stream().anyMatch(v -> v.getBeginLine() == 9),
                "Expected violation at line 9 (item: SerializableSubtype implements Serializable)");
    }

    @Test
    void typeIsExactlyMatchesExactType() {
        File kotlinFile = getResource(TYPE_IS_RESOURCE_DIR + "/CalendarUsage.kt");
        Report report = runXPath("//PropertyDeclaration[pmd-kotlin:typeIsExactly('java.util.Calendar')]", kotlinFile);
        assertTrue(report.getProcessingErrors().isEmpty(), "No processing errors expected");
        assertTrue(report.getViolations().stream().anyMatch(v -> v.getBeginLine() == 6),
                "Expected violation at line 6 (meeting: Calendar)");
    }

    @Test
    void typeIsExactlyDoesNotMatchSubtype() {
        // typeIsExactly('java.io.Serializable') must NOT match a property of type
        // SerializableSubtype (which implements Serializable but is not exactly it).
        File kotlinFile = getResource(TYPE_IS_RESOURCE_DIR + "/SerializableSubtype.kt");
        Report report = runXPath("//PropertyDeclaration[pmd-kotlin:typeIsExactly('java.io.Serializable')]", kotlinFile);
        assertTrue(report.getProcessingErrors().isEmpty(), "No processing errors expected");
        assertEquals(0, report.getViolations().size(),
                "typeIsExactly should not match properties of SerializableSubtype");
    }

    private Report runXPath(String xpathExpr, File kotlinFile) {
        PMDConfiguration config = new PMDConfiguration();
        config.setIgnoreIncrementalAnalysis(true);
        config.setDefaultLanguageVersion(
                LanguageRegistry.PMD.getLanguageById("kotlin").getDefaultVersion());

        try (PmdAnalysis pmd = PmdAnalysis.create(config)) {
            pmd.addRuleSet(RuleSet.forSingleRule(buildXPathRule(xpathExpr)));
            pmd.files().addFile(kotlinFile.toPath());
            return pmd.performAnalysisAndCollectReport();
        }
    }

    private Rule buildXPathRule(String xpathExpr) {
        XPathRule rule = new XPathRule(XPathVersion.DEFAULT, xpathExpr);
        rule.setLanguage(LanguageRegistry.PMD.getLanguageById("kotlin"));
        rule.setMessage("test");
        rule.setName("TestRule");
        return rule;
    }

    private File getResource(String path) {
        URL resource = getClass().getClassLoader().getResource(path);
        if (resource == null) {
            throw new IllegalStateException("Cannot find resource: " + path);
        }
        return new File(resource.getFile());
    }
}
