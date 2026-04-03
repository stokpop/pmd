/*
 * BSD-style license; for more info see http://pmd.sourceforge.net/license.html
 */

package net.sourceforge.pmd.lang.kotlin.rule.xpath.internal;

/**
 * Holds the active {@link KotlinTypeAnalysisContext} for XPath function evaluation.
 *
 * <p>Thread-local storage takes precedence over the global slot, allowing per-test
 * injection without interfering with other threads.
 *
 * <p>In production, call {@link #setGlobal(KotlinTypeAnalysisContext)} once at startup
 * (e.g., after loading a pre-computed JSON analysis file).
 * In tests, call {@link #set(KotlinTypeAnalysisContext)} before the test and
 * {@link #clear()} in an {@code @AfterEach} to avoid leaking state.
 */
public final class KotlinTypeAnalysisContextHolder {

    private static volatile KotlinTypeAnalysisContext globalContext = KotlinTypeAnalysisContext.empty();
    private static final ThreadLocal<KotlinTypeAnalysisContext> threadContext = new ThreadLocal<>();

    private KotlinTypeAnalysisContextHolder() {}

    /** Sets the context for the current thread (overrides the global context). */
    public static void set(KotlinTypeAnalysisContext ctx) {
        threadContext.set(ctx);
    }

    /** Sets the global context used when no thread-local context is active. */
    public static void setGlobal(KotlinTypeAnalysisContext ctx) {
        globalContext = ctx;
    }

    /**
     * Returns the active context: thread-local if set, otherwise global.
     * Never returns {@code null}; falls back to the empty no-op context.
     */
    public static KotlinTypeAnalysisContext get() {
        KotlinTypeAnalysisContext ctx = threadContext.get();
        return ctx != null ? ctx : globalContext;
    }

    /** Removes the thread-local context for the current thread. */
    public static void clear() {
        threadContext.remove();
    }

    /** Resets the global context to the empty no-op context. */
    public static void clearGlobal() {
        globalContext = KotlinTypeAnalysisContext.empty();
    }
}
