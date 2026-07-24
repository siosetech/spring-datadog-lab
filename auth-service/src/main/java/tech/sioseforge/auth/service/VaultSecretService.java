package tech.sioseforge.auth.service;

public interface VaultSecretService {
    void writeDatadogApiKey(String apiKey);
    String readDatadogApiKey();
}
