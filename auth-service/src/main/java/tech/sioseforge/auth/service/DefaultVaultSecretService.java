package tech.sioseforge.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

@Service
public class DefaultVaultSecretService implements VaultSecretService {

    private static final Logger log = LoggerFactory.getLogger(DefaultVaultSecretService.class);
    private final org.springframework.vault.core.VaultTemplate vaultTemplate;
    private final io.opentelemetry.api.trace.Tracer tracer;

    public DefaultVaultSecretService(org.springframework.vault.core.VaultTemplate vaultTemplate, io.opentelemetry.api.trace.Tracer tracer) {
        this.vaultTemplate = vaultTemplate;
        this.tracer = tracer;
    }

    public record DatadogSecret(@com.fasterxml.jackson.annotation.JsonProperty("api_key") String apiKey) {}

    @Override
    public void writeDatadogApiKey(String apiKey) {
        log.info("Writing Datadog API key to Vault...");
        vaultTemplate.opsForVersionedKeyValue("secret").put("datadog", new DatadogSecret(apiKey));
    }

    @Override
    @Retryable(
            retryFor = { RuntimeException.class },
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000)
    )
    public String readDatadogApiKey() {
        io.opentelemetry.api.trace.Span vaultSpan = tracer.spanBuilder("vault.read_secret").startSpan();
        try (var scope = vaultSpan.makeCurrent()) {
            log.info("Attempting to read Datadog API Key from Vault...");
            
            vaultSpan.setAttribute("vault.path", "secret/datadog");
            vaultSpan.addEvent("vault.read.started");

            org.springframework.vault.support.Versioned<DatadogSecret> response = 
                    vaultTemplate.opsForVersionedKeyValue("secret").get("datadog", DatadogSecret.class);
                    
            if (response != null && response.hasData() && response.getData() != null) {
                vaultSpan.addEvent("vault.read.success");
                return response.getData().apiKey();
            }
            
            vaultSpan.addEvent("vault.read.failed");
            throw new RuntimeException("Vault data not found or invalid format");
        } catch (Exception ex) {
            vaultSpan.recordException(ex);
            throw ex;
        } finally {
            vaultSpan.end();
        }
    }

    @Recover
    public String recoverDatadogApiKey(RuntimeException e) {
        log.error("All retries failed for reading Datadog API Key from Vault. Error: {}", e.getMessage());
        return "fallback-datadog-key-12345";
    }
}
