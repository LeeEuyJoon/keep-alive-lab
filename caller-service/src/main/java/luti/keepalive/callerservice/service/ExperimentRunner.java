package luti.keepalive.callerservice.service;

import luti.keepalive.callerservice.dto.CallResult;
import luti.keepalive.callerservice.dto.ExperimentResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.sun.management.UnixOperatingSystemMXBean;

import java.lang.management.ManagementFactory;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

@Service
public class ExperimentRunner {

	private static final Logger log = LoggerFactory.getLogger(ExperimentRunner.class);
	private static final long PRE_WARM_TIMEOUT_MS = 3000;

	private final ExternalApiClient externalApiClient;

	public ExperimentRunner(ExternalApiClient externalApiClient) {
		this.externalApiClient = externalApiClient;
	}

	public ExperimentResult run(String scenario, WebClient webClient, int totalRequests, long intervalMs) {
		return run(scenario, webClient, totalRequests, intervalMs, false, 0);
	}

	public ExperimentResult run(String scenario, WebClient webClient, int totalRequests, long intervalMs,
								boolean useWarmUp, long warmUpIntervalMs) {
		externalApiClient.reset();
		preWarm(scenario, webClient);

		List<Long> latencies = new ArrayList<>();
		int successCount = 0, timeoutCount = 0, errorCount = 0, coldConnectionCount = 0;

		for (int i = 0; i < totalRequests; i++) {
			if (i > 0) {
				sleep(scenario, webClient, intervalMs, useWarmUp ? warmUpIntervalMs : 0);
			}

			CallResult result = externalApiClient.callWork(webClient);
			latencies.add(result.latencyMs());

			if (result.success()) {
				successCount++;
				if (result.coldConnection())
					coldConnectionCount++;
			} else if ("timeout".equals(result.error())) {
				timeoutCount++;
			} else {
				errorCount++;
			}

			log.info("[{}] {}/{} - {}ms {}",
					 scenario, i + 1, totalRequests, result.latencyMs(),
					 result.success() ? (result.coldConnection() ? "cold" : "warm") : result.error());
		}

		return aggregate(scenario, totalRequests, successCount, timeoutCount, errorCount, coldConnectionCount,
						 latencies);
	}

	private void preWarm(String scenario, WebClient webClient) {
		log.info("[{}] pre-warm 시작", scenario);
		try {
			webClient.get().uri("/work")
					 .retrieve()
					 .bodyToMono(String.class)
					 .timeout(Duration.ofMillis(PRE_WARM_TIMEOUT_MS))
					 .block();
			log.info("[{}] pre-warm 완료", scenario);
		} catch (Exception e) {
			log.warn("[{}] pre-warm 실패 (무시): {}", scenario, e.getMessage());
		}
	}

	/**
	 * intervalMs 동안 sleep. useWarmUp이면 warmUpIntervalMs마다 /ping 호출.
	 *
	 * warm-up-on 타이밍 (intervalMs=30s, warmUpIntervalMs=10s):
	 *   실제 요청 → sleep 10s → /ping → sleep 10s → /ping → sleep 10s → 실제 요청
	 */
	private void sleep(String scenario, WebClient webClient, long intervalMs, long warmUpIntervalMs) {
		if (warmUpIntervalMs <= 0) {
			doSleep(intervalMs);
			return;
		}
		long elapsed = 0;
		while (elapsed < intervalMs) {
			long sleepTime = Math.min(warmUpIntervalMs, intervalMs - elapsed);
			doSleep(sleepTime);
			elapsed += sleepTime;
			if (elapsed < intervalMs) {
				sendPing(scenario, webClient);
			}
		}
	}

	private void sendPing(String scenario, WebClient webClient) {
		try {
			webClient.get().uri("/ping")
					 .retrieve()
					 .bodyToMono(String.class)
					 .timeout(Duration.ofSeconds(5))
					 .block();
			log.info("[{}] /ping 전송 (warm-up)", scenario);
		} catch (Exception e) {
			log.warn("[{}] /ping 실패: {}", scenario, e.getMessage());
		}
	}

	private void doSleep(long ms) {
		try {
			Thread.sleep(ms);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private ExperimentResult aggregate(String scenario, int totalRequests,
									   int successCount, int timeoutCount, int errorCount,
									   int coldConnectionCount, List<Long> latencies) {
		Collections.sort(latencies);
		double timeoutRate = totalRequests > 0 ? (double)timeoutCount / totalRequests : 0;
		double avgLatency = latencies.stream().mapToLong(Long::longValue).average().orElse(0);
		long p50 = percentile(latencies, 50);
		long p95 = percentile(latencies, 95);
		long p99 = percentile(latencies, 99);
		return new ExperimentResult(scenario, totalRequests, successCount, timeoutCount, errorCount,
									timeoutRate, coldConnectionCount, avgLatency, p50, p95, p99, -1L, -1L);
	}

	private ExperimentResult aggregateWithResources(String scenario, int totalRequests,
													 int successCount, int timeoutCount, int errorCount,
													 int coldConnectionCount, List<Long> latencies,
													 long heapUsedMb, long openFdCount) {
		Collections.sort(latencies);
		double timeoutRate = totalRequests > 0 ? (double)timeoutCount / totalRequests : 0;
		double avgLatency = latencies.stream().mapToLong(Long::longValue).average().orElse(0);
		long p50 = percentile(latencies, 50);
		long p95 = percentile(latencies, 95);
		long p99 = percentile(latencies, 99);
		return new ExperimentResult(scenario, totalRequests, successCount, timeoutCount, errorCount,
									timeoutRate, coldConnectionCount, avgLatency, p50, p95, p99,
									heapUsedMb, openFdCount);
	}

	private long snapshotHeapMb() {
		Runtime rt = Runtime.getRuntime();
		return (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
	}

	private long snapshotOpenFd() {
		try {
			var osBean = ManagementFactory.getOperatingSystemMXBean();
			if (osBean instanceof UnixOperatingSystemMXBean unixBean) {
				return unixBean.getOpenFileDescriptorCount();
			}
		} catch (Exception ignored) {}
		return -1;
	}

	private long percentile(List<Long> sorted, int p) {
		if (sorted.isEmpty())
			return 0;
		int index = (int)Math.ceil(p / 100.0 * sorted.size()) - 1;
		return sorted.get(Math.max(0, index));
	}

	/**
	 * concurrency개 요청을 동시에 발사. repeat번 반복.
	 * 리소스 스냅샷(heap, FD)을 실험 후에 측정한다.
	 */
	public ExperimentResult runConcurrent(String scenario, WebClient webClient,
										  int concurrency, int repeat) {
		externalApiClient.reset();
		preWarm(scenario, webClient);

		ExecutorService executor = Executors.newCachedThreadPool();
		List<Long> latencies = new ArrayList<>();
		int successCount = 0, timeoutCount = 0, errorCount = 0, coldConnectionCount = 0;

		for (int r = 0; r < repeat; r++) {
			List<CompletableFuture<CallResult>> futures = IntStream.range(0, concurrency)
																   .mapToObj(i -> CompletableFuture.supplyAsync(
																	   () -> externalApiClient.callWork(webClient), executor))
																   .toList();

			for (CompletableFuture<CallResult> future : futures) {
				try {
					CallResult result = future.join();
					latencies.add(result.latencyMs());
					if (result.success()) {
						successCount++;
						if (result.coldConnection()) coldConnectionCount++;
					} else if ("timeout".equals(result.error())) {
						timeoutCount++;
					} else {
						errorCount++;
					}
				} catch (Exception e) {
					errorCount++;
					latencies.add(0L);
					log.warn("[{}] 요청 예외: {}", scenario, e.getMessage());
				}
			}

			log.info("[{}] {}/{} 라운드 완료", scenario, r + 1, repeat);
		}

		executor.shutdown();

		int totalRequests = concurrency * repeat;
		long heapUsedMb = snapshotHeapMb();
		long openFdCount = snapshotOpenFd();

		return aggregateWithResources(scenario, totalRequests, successCount, timeoutCount,
									  errorCount, coldConnectionCount, latencies, heapUsedMb, openFdCount);
	}

}