package luti.keepalive.externalapi.dto;

import java.time.Instant;

public record WorkResponse(Instant processAt, long delayMs) {
}
