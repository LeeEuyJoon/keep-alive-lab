package luti.keepalive.externalapi.controller;

import jakarta.servlet.http.HttpServletRequest;
import luti.keepalive.externalapi.dto.WorkResponse;
import luti.keepalive.externalapi.service.ConnectionTracker;
import luti.keepalive.externalapi.service.WorkService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class WorkController {

    private final WorkService workService;
    private final ConnectionTracker connectionTracker;

    public WorkController(WorkService workService, ConnectionTracker connectionTracker) {
        this.workService = workService;
        this.connectionTracker = connectionTracker;
    }

    @GetMapping("/work")
    public ResponseEntity<WorkResponse> work(HttpServletRequest request) throws InterruptedException {
        int remotePort = request.getRemotePort();
        return ResponseEntity.ok(workService.doWork(remotePort));
    }

    @GetMapping("/ping")
    public ResponseEntity<String> ping() {
        return ResponseEntity.ok("pong");
    }

    @PostMapping("/reset")
    public ResponseEntity<Void> reset() {
        connectionTracker.reset();
        return ResponseEntity.ok().build();
    }
}
