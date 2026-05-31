package luti.keepalive.callerservice.service;

import luti.keepalive.callerservice.dto.CallResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.concurrent.TimeoutException;

@Service
public class ExternalApiClient {

    private final WebClient webClient;

    @Value("${experiment.timeout-ms:100}")
    private long timeoutMs;

    public ExternalApiClient(WebClient webClient) {
        this.webClient = webClient;
    }

    public CallResult callWork() {
        long start = System.currentTimeMillis();

        try {
            webClient.get()
                     .uri("/work")
                     .retrieve()
                     .bodyToMono(String.class)
                     .timeout(Duration.ofMillis(timeoutMs))
                     .block();

            long latency = System.currentTimeMillis() - start;
            return CallResult.success(latency);

        } catch (Exception e) {
            long latency = System.currentTimeMillis() - start;
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof TimeoutException) {
                return CallResult.failure(latency, "timeout");
            }
            return CallResult.failure(latency, e.getMessage());
        }
    }
}
