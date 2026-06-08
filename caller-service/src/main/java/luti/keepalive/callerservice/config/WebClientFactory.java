package luti.keepalive.callerservice.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

@Component
public class WebClientFactory {

	@Value("${external-api.base-url}")
	private String baseUrl;

	/** Scenario 1: 매 요청 Connection: Close */
	public WebClient noKeepAlive() {
		return WebClient.builder()
			.baseUrl(baseUrl)
			.defaultHeader("Connection", "close")
			.build();
	}

	/** Scenario 2~6: maxIdleTime 커스텀 */
	public WebClient withIdleTimeout(Duration maxIdleTime) {
		ConnectionProvider provider = ConnectionProvider.builder("pool-idle-" + maxIdleTime.toSeconds() + "s")
			.maxConnections(10)
			.maxIdleTime(maxIdleTime)
			.build();
		HttpClient httpClient = HttpClient.create(provider);
		return WebClient.builder()
			.baseUrl(baseUrl)
			.clientConnector(new ReactorClientHttpConnector(httpClient))
			.build();
	}

}
