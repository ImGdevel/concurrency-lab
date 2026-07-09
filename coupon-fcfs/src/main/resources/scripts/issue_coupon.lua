-- KEYS[1] = stock key, KEYS[2] = issued-user-set key
-- ARGV[1] = userId
-- 재고 확인 -> 중복발급 확인 -> 차감 -> 발급기록을 하나의 원자적 연산으로 묶는다.
-- 반환값: -1 = 품절, -2 = 이미 발급받음, N(>=0) = 발급 성공 후 남은 재고
local stock = tonumber(redis.call('GET', KEYS[1]))
if stock == nil or stock <= 0 then
    return -1
end

if redis.call('SISMEMBER', KEYS[2], ARGV[1]) == 1 then
    return -2
end

redis.call('SADD', KEYS[2], ARGV[1])
local remaining = redis.call('DECR', KEYS[1])
return remaining
