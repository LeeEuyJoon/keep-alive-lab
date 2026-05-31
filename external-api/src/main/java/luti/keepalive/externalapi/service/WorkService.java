package luti.keepalive.externalapi.service;

import luti.keepalive.externalapi.dto.WorkResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class WorkService {

    @Value("${work.delay-ms:50}")
    private long delayMs;

    @Value("${cold.connection.penalty-ms:60}")
    private long penaltyMs;

    private final ConnectionTracker connectionTracker;

    public WorkService(ConnectionTracker connectionTracker) {
        this.connectionTracker = connectionTracker;
    }

    public WorkResponse doWork(int remotePort) throws InterruptedException {
        boolean isCold = connectionTracker.isNewConnection(remotePort);

        if (isCold) {
            Thread.sleep(penaltyMs);
        }

        Thread.sleep(delayMs);
        return new WorkResponse(Instant.now(), delayMs, isCold);
    }
}
