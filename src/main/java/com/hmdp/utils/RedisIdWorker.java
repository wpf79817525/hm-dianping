package com.hmdp.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class RedisIdWorker {
    //2023-01-01 00:00:00 的时间戳
    private final long BEGIN_SECONDS = 1672531200L;
    public StringRedisTemplate stringRedisTemplate;

    public RedisIdWorker(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public long nextId(String keyPreFix) {
        // 获取当前时间
        LocalDateTime now = LocalDateTime.now();
        // 获取当前时间的时间戳
        long nowTimeStamp = now.toEpochSecond(ZoneOffset.UTC);
        // 计算ID构成中的时间戳
        long timeStamp = nowTimeStamp - BEGIN_SECONDS;
        // 将当前时间按指定格式转换为字符串形式
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        // 获取id后32位的部分
        long id = stringRedisTemplate.opsForValue().increment("irc:" + keyPreFix + ":" + date);
        // 将时间戳和id进行拼接
        long real_id = (timeStamp << 32) | id;
        return real_id;
    }

}
