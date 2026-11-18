package com.example.reporting_service.async;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * The 6.2 challenge exactly as specified: run the report query; if it
 * finishes within the configured threshold (default 3s), return the result
 * synchronously; if not, DON'T cancel it — let it keep running in the
 * background and return control to the caller so it can switch to the
 * 202 + jobId + poll flow. The already-running CompletableFuture is handed
 * back so the caller can attach its own completion handling (persisting the
 * result to report_jobs) without this class needing to know anything about
 * ReportJob at all — keeps the timeout mechanism reusable for any of the
 * five report types.
 */
@Component
public class AsyncReportExecutor implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(AsyncReportExecutor.class);

    private final ExecutorService executor = Executors.newFixedThreadPool(4, runnable -> {
        Thread t = new Thread(runnable, "report-worker");
        t.setDaemon(true);
        return t;
    });

    private final long syncTimeoutSeconds;

    public AsyncReportExecutor(@Value("${app.reporting.sync-timeout-seconds:3}") long syncTimeoutSeconds) {
        this.syncTimeoutSeconds = syncTimeoutSeconds;
    }

    public sealed interface Outcome<T> permits Outcome.Immediate, Outcome.Pending {
        record Immediate<T>(T result) implements Outcome<T> {
        }

        record Pending<T>(CompletableFuture<T> future) implements Outcome<T> {
        }
    }

    /**
     * @param work must be safe to run on a worker thread with no ambient
     *             request/TenantContext of its own — the caller is
     *             responsible for capturing and re-applying whatever
     *             thread-local state it needs INSIDE the supplier (see
     *             ReportQueryService's usage: it takes tenantSchema as an
     *             explicit parameter rather than reading TenantContext).
     */
    public <T> Outcome<T> run(Supplier<T> work) {
        CompletableFuture<T> future = CompletableFuture.supplyAsync(work, executor);
        try {
            T result = future.get(syncTimeoutSeconds, TimeUnit.SECONDS);
            return new Outcome.Immediate<>(result);
        } catch (TimeoutException e) {
            log.info("Report query exceeded {}s sync threshold — handing off to async job flow", syncTimeoutSeconds);
            return new Outcome.Pending<>(future);
        } catch (Exception e) {
            // Genuine failure within the timeout window — surface immediately
            // rather than pretending it's still "pending".
            throw new ReportExecutionException("Report query failed", e);
        }
    }

    @Override
    public void destroy() {
        executor.shutdown();
    }
}
