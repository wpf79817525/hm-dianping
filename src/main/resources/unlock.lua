-- 判断是否是自己的锁，如果是则释放，否则不做任何操作
if redis.call('get',KEYS[1]) == ARGV[1] then
    return redis.call('del',KEYS[1])
end
return 0