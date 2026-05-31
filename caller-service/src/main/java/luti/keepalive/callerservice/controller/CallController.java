package luti.keepalive.callerservice.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import luti.keepalive.callerservice.dto.CallResult;
import luti.keepalive.callerservice.service.ExternalApiClient;

@RestController
public class CallController {

	private final ExternalApiClient externalApiClient;

	public CallController(ExternalApiClient externalApiClient) {
		this.externalApiClient = externalApiClient;
	}

	@GetMapping("/call-once")
	public ResponseEntity<CallResult> callOnce() {
		return ResponseEntity.ok(externalApiClient.callWork());
	}
}
