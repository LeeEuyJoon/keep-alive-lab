package luti.keepalive.callerservice.dto;

public record CallResult(
	boolean success,
	long latencyMs,
	String error,
	boolean coldConnection
) {

	public static CallResult success(long latencyMs, boolean coldConnection) {
		return new CallResult(true, latencyMs, null, coldConnection);
	}

	public static CallResult failure(long latencyMs, String error) {
		return new CallResult(false, latencyMs, error, false);
	}
}
