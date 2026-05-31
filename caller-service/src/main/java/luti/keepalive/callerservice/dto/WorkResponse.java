package luti.keepalive.callerservice.dto;

import java.time.Instant;

public record WorkResponse(
	Instant processedAt,
	long delayMs,
	boolean coldConnection
) {
}
