package com.lab.couponfcfs.service;

import com.lab.couponfcfs.dto.IssueResponse;
import com.lab.couponfcfs.dto.StockResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CouponService {

    private static final String STOCK_PREFIX = "coupon:stock:";
    private static final String ISSUED_PREFIX = "coupon:issued:";

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> issueCouponScript;

    public void init(String couponId, long stock) {
        redisTemplate.opsForValue().set(STOCK_PREFIX + couponId, String.valueOf(stock));
        redisTemplate.delete(ISSUED_PREFIX + couponId);
    }

    public StockResponse getStock(String couponId) {
        String value = redisTemplate.opsForValue().get(STOCK_PREFIX + couponId);
        long stock = value == null ? 0 : Long.parseLong(value);
        return new StockResponse(couponId, stock);
    }

    /**
     * 의도적으로 race condition을 재현하는 버전.
     * "재고 확인"과 "재고 차감" 사이에 원자성이 없어 동시 요청 시 재고보다 많이 발급될 수 있다.
     */
    public IssueResponse issueNaive(String couponId, String userId) {
        String stockKey = STOCK_PREFIX + couponId;
        String issuedKey = ISSUED_PREFIX + couponId;

        String stockStr = redisTemplate.opsForValue().get(stockKey);
        long stock = stockStr == null ? 0 : Long.parseLong(stockStr);

        if (stock <= 0) {
            return new IssueResponse(couponId, userId, false, "SOLD_OUT", 0);
        }
        if (Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(issuedKey, userId))) {
            return new IssueResponse(couponId, userId, false, "ALREADY_ISSUED", stock);
        }

        // 의도적 지연: check-then-act 사이 경쟁 구간을 넓혀 동시성 버그를 쉽게 재현하기 위함.
        try {
            Thread.sleep(5);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        Long remaining = redisTemplate.opsForValue().decrement(stockKey);
        redisTemplate.opsForSet().add(issuedKey, userId);
        return new IssueResponse(couponId, userId, true, "ISSUED", remaining == null ? -1 : remaining);
    }

    /**
     * Redis Lua 스크립트로 재고확인+중복확인+차감+발급기록을 원자적으로 처리하는 버전.
     */
    public IssueResponse issue(String couponId, String userId) {
        Long result = redisTemplate.execute(
                issueCouponScript,
                List.of(STOCK_PREFIX + couponId, ISSUED_PREFIX + couponId),
                userId
        );

        if (result == null || result == -1L) {
            return new IssueResponse(couponId, userId, false, "SOLD_OUT", 0);
        }
        if (result == -2L) {
            return new IssueResponse(couponId, userId, false, "ALREADY_ISSUED", getStock(couponId).stock());
        }
        return new IssueResponse(couponId, userId, true, "ISSUED", result);
    }
}
