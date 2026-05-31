package luti.keepalive.callerservice.dto;

public record CallResult(boolean success, long latencyMs, String error) {

	public static CallResult success(long latencyMs) {
		return new CallResult(true, latencyMs, null);
	}

	public static CallResult failure(long latencyMs, String error) {
		return new CallResult(false, latencyMs, error);
	}
}
