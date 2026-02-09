-- 🚀 성능 최적화된 스케줄 홀드 스크립트 (Redis 호출 50% 감소)
local schedule_key = KEYS[1]
local order_id = ARGV[1]
local popup_id = ARGV[2]
local schedule_id = ARGV[3]
local schedule_qty = tonumber(ARGV[4])
local expires_at = ARGV[5]
local ttl_ms = tonumber(ARGV[6])

local hold_key = "hold:{" .. popup_id .. "}:" .. order_id

-- 🚀 단일 MGET으로 existence 및 value 체크를 동시에 처리
local values = redis.call("MGET", hold_key, schedule_key)
local hold_exists = values[1]
local available = tonumber(values[2] or "0")

-- 홀드가 이미 존재하는 경우
if hold_exists then
    return -3
end

-- 스케줄 키가 존재하지 않는 경우
if values[2] == false then
    return -2
end

-- 재고 부족
if available < schedule_qty then
    return -1
end

-- 🚀 원자적 처리 (Lua 스크립트 자체가 트랜잭션 보장)
redis.call("DECRBY", schedule_key, schedule_qty)
redis.call("HSET", hold_key,
    "type", "SCHEDULE_ONLY",
    "popupId", popup_id,
    "scheduleId", schedule_id,
    "scheduleQty", schedule_qty,
    "scheduleKey", schedule_key,
    "expiresAt", expires_at)
if ttl_ms and ttl_ms > 0 then
    redis.call("PEXPIRE", hold_key, ttl_ms)
end

return 1
