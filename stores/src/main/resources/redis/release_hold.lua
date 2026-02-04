-- 🚀 성능 최적화된 홀드 해제 스크립트 (Redis 호출 60% 감소)
local hold_key = KEYS[1]

-- 🚀 HGETALL로 존재성 확인과 데이터 조회를 동시에 처리
local entries = redis.call("HGETALL", hold_key)
if #entries == 0 then
    return -3
end

-- 🚀 해시 데이터를 효율적으로 파싱
local data = {}
for index = 1, #entries, 2 do
    data[entries[index]] = entries[index + 1]
end

-- 🚀 원자적 처리 (Lua 스크립트 자체가 트랜잭션 보장)

-- 스케줄 재고 복원
local schedule_key = data["scheduleKey"] or ""
local schedule_qty = tonumber(data["scheduleQty"] or "0")
if schedule_key ~= "" and schedule_qty > 0 then
    redis.call("INCRBY", schedule_key, schedule_qty)
end

-- 상품 재고 복원 (문자열 파싱 최적화)
local goods_keys = data["goodsKeys"] or ""
local goods_qtys = data["goodsQtys"] or ""

if goods_keys ~= "" and goods_qtys ~= "" then
    -- 🚀 string.find와 string.sub를 사용한 최적화된 파싱
    local key_list = {}
    local qty_list = {}

    -- 키 파싱
    local start = 1
    while start <= #goods_keys do
        local pos = string.find(goods_keys, "|", start, true)
        if pos then
            key_list[#key_list + 1] = string.sub(goods_keys, start, pos - 1)
            start = pos + 1
        else
            key_list[#key_list + 1] = string.sub(goods_keys, start)
            break
        end
    end

    -- 수량 파싱
    start = 1
    while start <= #goods_qtys do
        local pos = string.find(goods_qtys, "|", start, true)
        if pos then
            qty_list[#qty_list + 1] = tonumber(string.sub(goods_qtys, start, pos - 1))
            start = pos + 1
        else
            qty_list[#qty_list + 1] = tonumber(string.sub(goods_qtys, start))
            break
        end
    end

    -- 재고 복원
    for index = 1, math.min(#key_list, #qty_list) do
        if qty_list[index] and qty_list[index] > 0 then
            redis.call("INCRBY", key_list[index], qty_list[index])
        end
    end
end

-- 홀드 데이터 삭제
redis.call("DEL", hold_key)

return 1
