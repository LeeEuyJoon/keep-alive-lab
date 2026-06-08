package luti.keepalive.callerservice.service;



import luti.keepalive.callerservice.dto.CallResult;
import luti.keepalive.callerservice.dto.WorkResponse;

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
        return callWork(this.webClient);
    }

    public CallResult callWork(WebClient client) {
        long start = System.currentTimeMillis();

        try {
            WorkResponse response = client.get()
                     .uri("/work")
                     .retrieve()
                     .bodyToMono(WorkResponse.class)
                     .timeout(Duration.ofMillis(timeoutMs))
                     .block();

            long latency = System.currentTimeMillis() - start;
            boolean isCold = response != null && response.coldConnection();
            return CallResult.success(latency, isCold);

        } catch (Exception e) {
            long latency = System.currentTimeMillis() - start;
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof TimeoutException) {
                return CallResult.failure(latency, "timeout");
            }
            return CallResult.failure(latency, e.getMessage());
        }
    }

    public void reset() {
        try {
            webClient.post().uri("/reset")
                .retrieve()
                .toBodilessEntity()
                .timeout(Duration.ofSeconds(5))
                .block();
        } catch (Exception ignored) {}
    }
}
