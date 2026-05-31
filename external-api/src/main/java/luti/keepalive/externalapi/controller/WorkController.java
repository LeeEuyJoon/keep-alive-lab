package luti.keepalive.externalapi.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import luti.keepalive.externalapi.dto.WorkResponse;
import luti.keepalive.externalapi.service.WorkService;

@RestController
public class WorkController {



	private final WorkService workService;

	public WorkController(WorkService workService) {
		this.workService = workService;
	}

	@GetMapping("/work")
	public ResponseEntity<WorkResponse> work() throws InterruptedException {
		return ResponseEntity.ok(workService.doWork());
	}

	@GetMapping("/ping")
	public ResponseEntity<String> ping() {
		return ResponseEntity.ok("pong");
	}

	@PostMapping("/reset")
	public ResponseEntity<Void> reset() {
		return ResponseEntity.ok().build();
	}

}
