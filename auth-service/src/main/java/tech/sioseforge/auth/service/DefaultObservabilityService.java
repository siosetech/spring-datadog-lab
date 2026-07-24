package tech.sioseforge.auth.service;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tech.sioseforge.auth.domain.view.TraceCheckResponseVO;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Service
public class DefaultObservabilityService implements ObservabilityService {

    private static final Logger log = LoggerFactory.getLogger(DefaultObservabilityService.class);
    private final Tracer tracer;
    
    // Virtual thread executor for async testing
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public DefaultObservabilityService(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    public TraceCheckResponseVO checkTrace() {
        Span span = tracer.spanBuilder("SimpleTraceCheck").startSpan();
        try (var scope = span.makeCurrent()) {
            log.info("Performing simple trace check");
            span.addEvent("checkTrace executed");
            return new TraceCheckResponseVO("OK", "Check completed", span.getSpanContext().getTraceId());
        } finally {
            span.end();
        }
    }

    @Override
    public TraceCheckResponseVO deepTraceCheck() {
        Span span = tracer.spanBuilder("DeepTraceCheck").startSpan();
        try (var scope = span.makeCurrent()) {
            log.info("Performing deep trace check with virtual threads");
            String currentTraceId = span.getSpanContext().getTraceId();
            
            // Virtual thread üzerinde çalışarak trace context'in taşınıp taşınmadığını test ediyoruz
            Future<?> future = executor.submit(() -> {
                Span asyncSpan = tracer.spanBuilder("async_background_task").startSpan();
                try {
                    log.info("Running inside virtual thread...");
                    asyncSpan.addEvent("Virtual thread task started");
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    asyncSpan.end();
                }
            });

            try {
                future.get();
            } catch (Exception e) {
                log.error("Async execution failed", e);
            }

            return new TraceCheckResponseVO("OK", "Deep check completed", currentTraceId);
        } finally {
            span.end();
        }
    }
}
