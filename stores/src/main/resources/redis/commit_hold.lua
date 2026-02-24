-- ✅ 확정된 HOLD 커밋 스크립트 (재고 복원 없이 홀드 해제)
local hold_key = KEYS[1]

if hold_key == nil then
    return -4
end

local exists = redis.call("EXISTS", hold_key)
if exists == 0 then
    return -3
end

redis.call("DEL", hold_key)
return 1
