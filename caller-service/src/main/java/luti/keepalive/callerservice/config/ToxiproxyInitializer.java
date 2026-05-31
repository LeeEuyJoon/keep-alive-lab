package luti.keepalive.callerservice.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class ToxiproxyInitializer {

	private static final Logger log = LoggerFactory.getLogger(ToxiproxyInitializer.class);

	@Value("${toxiproxy.api-url}")
	private String toxiproxyApiUrl;

	@Value("${toxiproxy.latency-ms:15}")
	private int latencyMs;

	@EventListener(ApplicationReadyEvent.class)
	public void init() {
		try {
			RestClient restClient = RestClient.create(toxiproxyApiUrl);

			String upstreamBody = """
                    {
                      "name": "latency_upstream",
                      "type": "latency",
                      "stream": "upstream",
                      "attributes": { "latency": %d, "jitter": 0 }
                    }
                    """.formatted(latencyMs);

			String downstreamBody = """
                    {
                      "name": "latency_downstream",
                      "type": "latency",
                      "stream": "downstream",
                      "attributes": { "latency": %d, "jitter": 0 }
                    }
                    """.formatted(latencyMs);

			restClient.post()
					  .uri("/proxies/external-api/toxics")
					  .header("Content-Type", "application/json")
					  .body(upstreamBody)
					  .retrieve()
					  .toBodilessEntity();

			restClient.post()
					  .uri("/proxies/external-api/toxics")
					  .header("Content-Type", "application/json")
					  .body(downstreamBody)
					  .retrieve()
					  .toBodilessEntity();

			log.info("Toxiproxy latency toxic 등록 완료 ({}ms upstream + downstream)", latencyMs);

		} catch (Exception e) {
			log.warn("Toxiproxy 초기화 실패 - 로컬 테스트 환경으로 간주하고 계속 진행. 원인: {}", e.getMessage());
		}
	}

}
