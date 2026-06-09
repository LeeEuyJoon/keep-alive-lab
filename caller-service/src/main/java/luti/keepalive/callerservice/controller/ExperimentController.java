package luti.keepalive.callerservice.controller;

import java.time.Duration;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;

import luti.keepalive.callerservice.config.WebClientFactory;
import luti.keepalive.callerservice.dto.ExperimentResult;
import luti.keepalive.callerservice.service.ExperimentRunner;

@RestController
@RequestMapping("/experiments")
public class ExperimentController {

	private final ExperimentRunner experimentRunner;
	private final WebClientFactory webClientFactory;

	public ExperimentController(ExperimentRunner experimentRunner, WebClientFactory webClientFactory) {
		this.experimentRunner = experimentRunner;
		this.webClientFactory = webClientFactory;
	}

	@PostMapping("/no-keep-alive")
	public ResponseEntity<ExperimentResult> noKeepAlive(
		@RequestParam(defaultValue = "20") int totalRequests,
		@RequestParam(defaultValue = "5000") long intervalMs) {
		WebClient client = webClientFactory.noKeepAlive();
		return ResponseEntity.ok(
			experimentRunner.run("NO_KEEP_ALIVE", client, totalRequests, intervalMs)
		);
	}

	@PostMapping("/short-idle")
	public ResponseEntity<ExperimentResult> shortIdle(
		@RequestParam(defaultValue = "20") int totalRequests,
		@RequestParam(defaultValue = "5000") long intervalMs) {
		WebClient client = webClientFactory.withIdleTimeout(Duration.ofSeconds(2));
		return ResponseEntity.ok(
			experimentRunner.run("SHORT_IDLE", client, totalRequests, intervalMs));
	}

	@PostMapping("/long-idle")
	public ResponseEntity<ExperimentResult> longIdle(
		@RequestParam(defaultValue = "20") int totalRequests,
		@RequestParam(defaultValue = "5000") long intervalMs) {
		WebClient client = webClientFactory.withIdleTimeout(Duration.ofSeconds(60));
		return ResponseEntity.ok(
			experimentRunner.run("LONG_IDLE", client, totalRequests, intervalMs));
	}

	/**
	 * external-api를 SERVER_TOMCAT_CONNECTION_TIMEOUT=2000 으로 재시작한 후 실행할 것
	 * SERVER_TOMCAT_CONNECTION_TIMEOUT=2000 docker compose up -d --build external-api
	 */
	@PostMapping("/server-short")
	public ResponseEntity<ExperimentResult> serverShort(
		@RequestParam(defaultValue = "20") int totalRequests,
		@RequestParam(defaultValue = "5000") long intervalMs) {
		WebClient client = webClientFactory.withIdleTimeout(Duration.ofSeconds(60));
		return ResponseEntity.ok(
			experimentRunner.run("SERVER_SHORT", client, totalRequests, intervalMs)
		 );
	}

	/**
	 * external-api를 SERVER_TOMCAT_CONNECTION_TIMEOUT=20000 으로 재시작한 후 실행할 것.
	 * SERVER_TOMCAT_CONNECTION_TIMEOUT=20000 docker compose up -d --build external-api
	 */
	@PostMapping("/warm-up-off")
	public ResponseEntity<ExperimentResult> warmUpOff(
		@RequestParam(defaultValue = "10") int totalRequests,
		@RequestParam(defaultValue = "30000") long intervalMs) {
		WebClient client = webClientFactory.withIdleTimeout(Duration.ofSeconds(60));
		return ResponseEntity.ok(
			experimentRunner.run("WARM_UP_OFF", client, totalRequests, intervalMs));
	}

	/**
	 * external-api를 SERVER_TOMCAT_CONNECTION_TIMEOUT=20000 으로 재시작한 후 실행할 것.
	 * SERVER_TOMCAT_CONNECTION_TIMEOUT=20000 docker compose up -d --build external-api
	 */
	@PostMapping("/warm-up-on")
	public ResponseEntity<ExperimentResult> warmUpOn(
		@RequestParam(defaultValue = "10") int totalRequests,
		@RequestParam(defaultValue = "30000") long intervalMs) {
		WebClient client = webClientFactory.withIdleTimeout(Duration.ofSeconds(60));
		return ResponseEntity.ok(
			experimentRunner.run("WARM_UP_ON", client, totalRequests, intervalMs, true, 10000));
	}

	/**
	 * keep-alive pool 크기별 리소스 비용 측정.
	 * maxConnections < concurrency → pool 고갈 → errorCount 증가
	 * maxConnections >= concurrency → 정상 처리 → heap/FD 더 점유
	 */
	@PostMapping("/resource-cost")
	public ResponseEntity<ExperimentResult> resourceCost(
		@RequestParam(defaultValue = "10") int maxConnections,
		@RequestParam(defaultValue = "10") int concurrency,
		@RequestParam(defaultValue = "3") int repeat) {
		WebClient client = webClientFactory.withPool(maxConnections);
		return ResponseEntity.ok(
			experimentRunner.runConcurrent(
				"RESOURCE_COST_pool" + maxConnections + "_c" + concurrency,
				client, concurrency, repeat));
	}

}
