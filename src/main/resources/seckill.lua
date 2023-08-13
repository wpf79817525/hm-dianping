-- 1. 变量
-- 1.1 voucherId
local voucherId = ARGV[1]
-- 1.2 userId
local userId = ARGV[2]
-- 1.3 voucherOrderId
local voucherOrderId = ARGV[3]

-- 2. 获取对应的Key
local voucherStockKey = "seckill:voucher:stock:" .. voucherId
local voucherOrderKey = "seckill:voucher:order:" .. voucherId

-- 3. 根据voucherStockKey查询剩余库存，看库存是否 < 1
if tonumber(redis.call("get",voucherStockKey)) < 1 then
    return 1
end
-- 4. 查看voucherOrderKey对应的集合是否已经包含用户id
if tonumber(redis.call("sismember",voucherOrderKey,userId)) > 0 then
    return 2
end
-- 5. 走到这，说明库存足够且用户没有重复购买，库存减一，添加订单
-- 5.1 库存减一
redis.call("incrby",voucherStockKey,-1)
-- 5.2 添加订单
redis.call("sadd",voucherOrderKey,userId)
-- 6. 将订单添加到消息队列 xadd messageQUEUE * k v ...
redis.call("xadd","stream.orders","*","userId",userId,"voucherId",voucherId,"id",voucherOrderId)
return 0