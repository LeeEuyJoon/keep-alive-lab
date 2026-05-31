package luti.keepalive.externalapi.service;

import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import luti.keepalive.externalapi.dto.WorkResponse;

@Service
public class WorkService {

	@Value("${work.delay-ms:50}")
	private long delayMs;

	public WorkResponse doWork() throws InterruptedException {
		Thread.sleep(delayMs); // DB 조회, 캐시 미스, 연산 등 비즈니스 로직 처리 시간 대용
		return new WorkResponse(Instant.now(), delayMs);
	}
}
