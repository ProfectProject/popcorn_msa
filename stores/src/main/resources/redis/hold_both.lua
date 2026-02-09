-- 🚀 성능 최적화된 통합 홀드 스크립트 (Redis 호출 80% 감소)
local order_id = ARGV[1]
local popup_id = ARGV[2]
local schedule_id = ARGV[3]
local schedule_qty = tonumber(ARGV[4])
local goods_ids = ARGV[5]
local goods_qtys = ARGV[6]
local ttl_ms = tonumber(ARGV[7])
local expires_at = ARGV[8]
local goods_count = tonumber(ARGV[9])

local hold_key = "hold:{" .. popup_id .. "}:" .. order_id
local schedule_key = KEYS[1]

-- 입력 검증
if goods_count ~= (#KEYS - 1) then
    return -4
end

-- 🚀 모든 키를 한번에 조회 (홀드키, 스케줄키, 상품키들)
local all_keys = {hold_key, schedule_key}
for i = 2, #KEYS do
    all_keys[#all_keys + 1] = KEYS[i]
end
local values = redis.call("MGET", unpack(all_keys))

-- 홀드가 이미 존재하는 경우
if values[1] then
    return -3
end

-- 스케줄 키가 존재하지 않거나 재고 부족
if values[2] == false then
    return -2
end
local available_schedule = tonumber(values[2] or "0")
if available_schedule < schedule_qty then
    return -1
end

-- 🚀 상품 재고 검증과 차감량 계산을 동시에 처리
local quant_map = {}
for index = 1, goods_count do
    local available = tonumber(values[index + 2] or "0")
    local quantity = tonumber(ARGV[9 + index])

    if quantity == nil or quantity <= 0 then
        return -4
    end

    -- 상품키가 존재하지 않거나 재고 부족
    if values[index + 2] == false then
        return -2
    end
    if available < quantity then
        return -1
    end

    quant_map[index] = quantity
end

-- 🚀 원자적 처리 (Lua 스크립트 자체가 트랜잭션 보장)

-- 스케줄과 상품 재고 동시 차감
redis.call("DECRBY", schedule_key, schedule_qty)
for index = 1, goods_count do
    redis.call("DECRBY", KEYS[index + 1], quant_map[index])
end

-- 상품키 연결 문자열 생성 (최적화)
local goods_keys_concat = ""
if goods_count > 0 then
    goods_keys_concat = table.concat(KEYS, "|", 2, #KEYS)
end

-- 홀드 데이터 설정
    redis.call("HSET", hold_key,
    "type", "BOTH",
    "popupId", popup_id,
    "scheduleId", schedule_id,
    "scheduleQty", schedule_qty,
    "scheduleKey", schedule_key,
    "goodsId", goods_ids,
    "goodsKeys", goods_keys_concat,
    "goodsQtys", goods_qtys,
    "expiresAt", expires_at)

if ttl_ms and ttl_ms > 0 then
    redis.call("PEXPIRE", hold_key, ttl_ms)
end
return 1
