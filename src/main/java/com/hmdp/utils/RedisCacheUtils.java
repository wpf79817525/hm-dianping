package com.hmdp.utils;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@Component
public class RedisCacheUtils {
    private StringRedisTemplate stringRedisTemplate;

    public RedisCacheUtils(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }
    // 将任意对象序列化为json并存储在string类型的key中，并且可以设置TTL过期时间
    public <T> void setObjectToCache(String key,T object, Long expireTime, TimeUnit timeUnit) {
        String jsonStr = JSONUtil.toJsonStr(object);
        stringRedisTemplate.opsForValue().set(key,jsonStr,expireTime,timeUnit);
    }

    // 将任意对象序列化为json并存储在string类型的key中，并且可以设置逻辑过期时间，用于处理缓存击穿问题
    public <T> void setObjectToCacheWithLogicalExpireTime(String key, T object, Long expireTime, TimeUnit timeUnit) {
        RedisData redisData = new RedisData();
        redisData.setData(object);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(timeUnit.toSeconds(expireTime)));
        String jsonStr = JSONUtil.toJsonStr(redisData);
        stringRedisTemplate.opsForValue().set(key,jsonStr);
    }

    // 根据指定id查询缓存，并反序列化为指定类型，利用缓存空值的方式解决缓存穿透问题
    public <ID,R> R queryByIdWithPassThrough
        (String keyPrefix, ID id, Class<R> type, Function<ID,R> function,Long expireTime, Long expireTimeNull,TimeUnit timeUnit) {
        // 如果id为null，直接返回
        if (id == null) {
            return null;
        }
        String key = keyPrefix + id;
        // 构建对应的key，查询缓存
        // 如果查询到数据，直接返回(该数据可能是空值)
        String jsonStr = stringRedisTemplate.opsForValue().get(key);
        if (jsonStr != null) {
            if (jsonStr.equals(""))
                return null;
            R object = JSONUtil.toBean(jsonStr, type);
            return object;
        }


        // 走到这说明查询不到数据，需要进入数据库进行查询(需要提供查询方法)
        R data = function.apply(id);
        // 查询到数据以后需要构建缓存
        // 数据库中也查询不到数据，需要缓存空值
        if (data == null) {
            stringRedisTemplate.opsForValue().set(key,"",expireTimeNull,timeUnit);
            return null;
        }
        // 数据库查询到了对应的数据，建立缓存
        setObjectToCache(key,data,expireTime,timeUnit);
        return data;


    }

    // 根据指定id查询缓存，并反序列化为指定类型，利用逻辑过期解决缓存击穿问题
    public <ID,R> R queryByIdWithLogicalExpire
        (String lockKey,String keyPrefix, ID id, Class<R> type, Function<ID,R> function,Long expireTime, Long expireTimeNull,TimeUnit timeUnit) {
        // 查询缓存(理想情况必定命中)
        String key = keyPrefix + id;
        String jsonStr = stringRedisTemplate.opsForValue().get(key);
        // 获取缓存未命中 或者 数据为空(防止缓存穿透)
        if (jsonStr == null || jsonStr.equals("")) {
            return null;
        }
        // 将jsonStr转为RedisData对象，并获取数据
        // 逻辑时间
        RedisData redisData = JSONUtil.toBean(jsonStr, RedisData.class);
        LocalDateTime logicalExpireTime = redisData.getExpireTime();
        JSONObject jsonObject = (JSONObject)  redisData.getData();
        // 数据
        R data = JSONUtil.toBean(jsonObject,type);
        // 如果当前逻辑期限有效(在当前时间之后)
        if (logicalExpireTime.isAfter(LocalDateTime.now()))
        {
            return data;
        }
        // 当前逻辑期限失效，尝试加互斥锁
        boolean success_lock = tryLock(lockKey);
        // 如果加锁失败，返回旧数据
        if (!success_lock)
            return data;
        // 如果加锁成功，则需要double check，再次查询缓存判断逻辑期限是否有效
        jsonStr = stringRedisTemplate.opsForValue().get(key);
        redisData = JSONUtil.toBean(jsonStr, RedisData.class);
        logicalExpireTime = redisData.getExpireTime();
        data = JSONUtil.toBean((JSONObject)  redisData.getData(),type);
        if (logicalExpireTime.isAfter(LocalDateTime.now()))
        {
            unLock(lockKey);
            return data;
        }
        // 如果double check后仍然无效，则需要查询数据库并重建缓存(开启一个新线程去查询，该线程自己返回旧数据)
        new Thread(() -> {
            try {
                // 查询数据库
                R apply = function.apply(id);
                // 如果在数据库中查不到数据，则需要在缓存设置空数据，防止缓存穿透
                if (apply == null) {
                    stringRedisTemplate.opsForValue().set(key,"",expireTimeNull,timeUnit);
                }
                // 在数据库查询到了对应的数据，则重建缓存，设置逻辑期限(这里调用时设置的逻辑期限是30分钟，因此测试不好看出效果)
                else
                {
                    setObjectToCacheWithLogicalExpireTime(key,apply,expireTime,timeUnit);
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                // 一定要释放锁
                unLock(lockKey);
            }

        }).start();
        // 返回旧数据
        return data;
    }
    // 加互斥锁
    private boolean tryLock(String lockKey) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "",3L,TimeUnit.MINUTES);
        return flag == null ? false : flag;
    }

    // 解锁
    private void unLock(String lockKey) {
        stringRedisTemplate.delete(lockKey);
    }
}
