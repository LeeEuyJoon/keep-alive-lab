package luti.keepalive.callerservice.dto;

public record ExperimentResult(
	String scenario,
	int totalRequests,
	int successCount,
	int timeoutCount,
	int errorCount,
	double timeoutRate,
	int coldConnectionCount,
	double avgLatencyMs,
	long p50LatencyMs,
	long p95LatencyMs,
	long p99LatencyMs,
	long heapUsedMb,
	long openFdCount
) {
}
