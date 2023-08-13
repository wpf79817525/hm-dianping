package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock{
    private StringRedisTemplate stringRedisTemplate;
    private String lockName;
    private static final String PREFIX = "lock:";
    private static final String uuid = UUID.fastUUID().toString();
    private static final DefaultRedisScript<Long> script;
    static {
        script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("unlock.lua"));
        script.setResultType(Long.class);
    }

    public SimpleRedisLock(StringRedisTemplate stringRedisTemplate, String lockName) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.lockName = lockName;
    }

    @Override
    public boolean tryLock(long timeoutSec) {
        String thread_id = uuid + Thread.currentThread().getId();
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(PREFIX + lockName, thread_id, timeoutSec, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(success);
    }

    // 无法保证原子性

//    public void unlock() {
//        String thread_id = uuid + Thread.currentThread().getId();
//        String cache_id = stringRedisTemplate.opsForValue().get(PREFIX + lockName);
//        if (thread_id.equals(cache_id))
//            stringRedisTemplate.delete(PREFIX + lockName);
//    }

    // 保证原子性
    @Override
    public void unlock() {
        stringRedisTemplate.execute(script, Collections.singletonList(PREFIX + lockName),uuid + Thread.currentThread().getId());
    }
}
