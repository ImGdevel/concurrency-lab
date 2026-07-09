package com.lab.queuesequential.service;

import com.lab.queuesequential.dto.EnterResponse;
import com.lab.queuesequential.dto.RankResponse;
import com.lab.queuesequential.dto.TokenIssueResponse;
import com.lab.queuesequential.exception.NotEnterableException;
import com.lab.queuesequential.exception.TokenNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class QueueService {

    private static final String SEQ_KEY = "queue:seq";
    private static final String WAITING_KEY = "queue:waiting";
    private static final String SESSION_PREFIX = "queue:session:";

    private final StringRedisTemplate redisTemplate;

    @Value("${queue.enter-threshold:100}")
    private long enterThreshold;

    @Value("${queue.session-ttl-seconds:600}")
    private long sessionTtlSeconds;

    public TokenIssueResponse issueToken() {
        String token = UUID.randomUUID().toString();
        // 클라이언트 시계 대신 Redis 서버 카운터로 순번을 매겨 clock skew를 배제한다.
        Long score = redisTemplate.opsForValue().increment(SEQ_KEY);
        redisTemplate.opsForZSet().add(WAITING_KEY, token, score);
        return new TokenIssueResponse(token, rankOf(token));
    }

    public RankResponse getRank(String token) {
        long rank = rankOf(token);
        if (rank < 0) {
            throw new TokenNotFoundException(token);
        }
        return new RankResponse(token, rank, rank < enterThreshold);
    }

    public EnterResponse enter(String token) {
        long rank = rankOf(token);
        if (rank < 0) {
            throw new TokenNotFoundException(token);
        }
        if (rank >= enterThreshold) {
            throw new NotEnterableException(rank);
        }

        redisTemplate.opsForZSet().remove(WAITING_KEY, token);
        String sessionId = UUID.randomUUID().toString();
        redisTemplate.opsForValue()
                .set(SESSION_PREFIX + token, sessionId, Duration.ofSeconds(sessionTtlSeconds));
        return new EnterResponse(token, true, sessionId);
    }

    private long rankOf(String token) {
        Long rank = redisTemplate.opsForZSet().rank(WAITING_KEY, token);
        return rank == null ? -1 : rank;
    }
}
