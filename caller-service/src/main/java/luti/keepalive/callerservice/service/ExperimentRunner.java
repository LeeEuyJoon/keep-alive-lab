package luti.keepalive.callerservice.service;

import luti.keepalive.callerservice.dto.CallResult;
import luti.keepalive.callerservice.dto.ExperimentResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class ExperimentRunner {

	private static final Logger log = LoggerFactory.getLogger(ExperimentRunner.class);

	private final ExternalApiClient externalApiClient;

	public ExperimentRunner(ExternalApiClient externalApiClient) {
		this.externalApiClient = externalApiClient;
	}

	public ExperimentResult run(String scenario, WebClient webClient, int totalRequests, long intervalMs) {
		List<Long> latencies = new ArrayList<>();
		int successCount = 0, timeoutCount = 0, errorCount = 0, coldConnectionCount = 0;

		for (int i = 0; i < totalRequests; i++) {
			if (i > 0) {
				try {
					Thread.sleep(intervalMs);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					break;
				}
			}

			CallResult result = externalApiClient.callWork(webClient);
			latencies.add(result.latencyMs());

			if (result.success()) {
				successCount++;
				if (result.coldConnection()) coldConnectionCount++;
			} else if ("timeout".equals(result.error())) {
				timeoutCount++;
			} else {
				errorCount++;
			}

			log.info("[{}] 요청 {}/{} - {}ms, {}",
					 scenario, i + 1, totalRequests, result.latencyMs(),
					 result.success() ? (result.coldConnection() ? "cold" : "warm") : result.error());
		}

		return aggregate(scenario, totalRequests, successCount, timeoutCount, errorCount, coldConnectionCount, latencies);
	}

	private ExperimentResult aggregate(String scenario, int totalRequests,
									   int successCount, int timeoutCount, int errorCount,
									   int coldConnectionCount, List<Long> latencies) {
		Collections.sort(latencies);

		double timeoutRate = totalRequests > 0 ? (double) timeoutCount / totalRequests : 0;
		double avgLatency = latencies.stream().mapToLong(Long::longValue).average().orElse(0);
		long p50 = percentile(latencies, 50);
		long p95 = percentile(latencies, 95);
		long p99 = percentile(latencies, 99);

		return new ExperimentResult(scenario, totalRequests, successCount, timeoutCount, errorCount,
									timeoutRate, coldConnectionCount, avgLatency, p50, p95, p99);
	}

	private long percentile(List<Long> sorted, int percentile) {
		if (sorted.isEmpty()) return 0;
		int index = (int) Math.ceil(percentile / 100.0 * sorted.size()) - 1;
		return sorted.get(Math.max(0, index));
	}
}