package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisCacheUtils;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import com.hmdp.utils.SystemConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.SHOP_GEO_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Autowired
    StringRedisTemplate stringRedisTemplate;

    @Autowired
    RedisCacheUtils redisCacheUtils;

    private final static ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    @Override
    public Result queryByShopType(Integer typeId, Integer current, Double x, Double y) {
        // TODO 查询商店，需要指定商店类型，根据距离远近得到特定页数的商店列表
        // 1. 如果x,y为null，则无需根据距离将shopId进行排序
        if (x == null || y == null)
        {
            // 根据类型分页查询
            Page<Shop> page = query()
                    .eq("type_id", typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            // 返回数据
            return Result.ok(page.getRecords());
        }
        // 2. 定义分页查询参数
        int start = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
        int count = current * SystemConstants.DEFAULT_PAGE_SIZE;
        // 3. 根据指定的typeId获取对应的key
        String key = SHOP_GEO_KEY + typeId;
        // 4. 在redis中查询特定数量的商店ids
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo().search(key,
                GeoReference.fromCoordinate(x, y),
                new Distance(5000),
                RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().limit(count).includeDistance());
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> content = results.getContent();
        if (content == null || content.isEmpty())
            return Result.ok(Collections.emptyList());
        // 防止查询到的总数 <= start
        if (start >= content.size())
            return Result.ok(Collections.emptyList());
        // 4.1 将ids从start开始截取
        List<Long> ids = new ArrayList<>(content.size() - start);
        Map<Long,Distance> distanceMap = new HashMap<>(content.size() - start);
        content.stream().skip(start).collect(Collectors.toList()).forEach(result -> {
            Distance distance = result.getDistance();
            RedisGeoCommands.GeoLocation<String> member = result.getContent();
            String shopIdStr = member.getName();
            Long shopId = Long.valueOf(shopIdStr);
            ids.add(shopId);
            distanceMap.put(shopId,distance);
        });

        // 5. 根据ids查询数据库
        String idsStr = StrUtil.join(",", ids);
        List<Shop> shops = query().in("id", ids).last("order by field(id," + idsStr + ")").list();

        // 6. 给Shop设置distance属性
        shops.forEach(shop -> {
            Long id = shop.getId();
            Distance distance = distanceMap.get(id);
            shop.setDistance(distance.getValue());
        });

        // 7. 返回数据
        return Result.ok(shops);
    }

    @Override
    public Shop queryById(Long id) {
//        return redisCacheUtils.queryByIdWithPassThrough
//        (RedisConstants.CACHE_SHOP_KEY,id,Shop.class,(pid) -> getById(pid),RedisConstants.CACHE_SHOP_TTL,RedisConstants.CACHE_NULL_TTL,TimeUnit.MINUTES);
        // 缓存穿透
//        return queryWithPassThrough(id);

        // 缓存击穿(互斥锁)
//        return queryWithMutex(id);

        // 缓存击穿(逻辑过期)
//        return queryWithLogicalExpire(id);
        return redisCacheUtils.queryByIdWithLogicalExpire
            (RedisConstants.LOCK_SHOP_KEY + id,RedisConstants.CACHE_SHOP_KEY,id,Shop.class,(pid) -> getById(pid),
            RedisConstants.CACHE_SHOP_TTL,RedisConstants.CACHE_NULL_TTL,TimeUnit.MINUTES);
    }


    // 解决缓存穿透问题
    private Shop queryWithPassThrough(Long id) {
        // 根据id查询商铺
        // 1. 先在Redis缓存中进行查询
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        String jsonStr = stringRedisTemplate.opsForValue().get(key);

        // 1.1 在Redis查询到(这里看情况设计，如果为空字符串需要返回Result.fail也行，这里我们返回的是查询到的空数据)
        if (jsonStr != null) {
            Shop shop = JSONUtil.toBean(jsonStr, Shop.class);
            return shop;
        }

        // 2. 在Redis没有查询到，在数据库进行查询
        Shop shop = getById(id);
        // 没有查询到，直接返回
        if (shop == null) {
            // 将空数据设置到缓存当中，避免缓存穿透
            stringRedisTemplate.opsForValue().set(key,"");
            // 设置的缓存期限短
            stringRedisTemplate.expire(key,RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        // 将查询到的数据写到Redis
        String json = JSONUtil.toJsonStr(shop);
        stringRedisTemplate.opsForValue().set(key,json);
        // 设置缓存期限
        stringRedisTemplate.expire(key,RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        return shop;
    }


    // 解决缓存击穿问题(互斥锁方式)
    private Shop queryWithMutex(Long id) {
        // 根据id查询商铺
        // 1. 先在Redis缓存中进行查询
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        String jsonStr = stringRedisTemplate.opsForValue().get(key);


        // 1.1 在Redis查询到(这里看情况设计，如果为空字符串需要返回Result.fail也行，这里我们返回的是查询到的空数据)
        if (jsonStr != null) {
            Shop shop = JSONUtil.toBean(jsonStr, Shop.class);
            return shop;
        }

        // 2. 在Redis没有查询到(未命中)，在数据库进行查询，这里在查询数据库之前之前要判断有没有锁
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);

        // 3. 判断是否加锁
        Shop shop = null;
        try {
            // 3.1 如果加锁了(加锁失败了)
            if (!isLock)
            {
                Thread.sleep(20);
                queryWithMutex(id);
            }
            // 3.2 如果没有加锁(加锁成功了)，先再次查询Redis，如果命中，则无需查询数据库
            // 3.2.1 再次查询Redis命中，无需查询数据库进行缓存修改
            jsonStr = stringRedisTemplate.opsForValue().get(key);
            if (jsonStr != null) {
                return JSONUtil.toBean(jsonStr, Shop.class);
            }

            // 3.2.2 查询Redis未命中，需查询数据库并进行缓存修改
            shop = getById(id);
            // 没有查询到，直接返回
            if (shop == null) {
                // 将空数据设置到缓存当中，避免缓存穿透
                stringRedisTemplate.opsForValue().set(key,"");
                // 设置的缓存期限短
                stringRedisTemplate.expire(key,RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            // 将查询到的数据写到Redis
            String json = JSONUtil.toJsonStr(shop);
            stringRedisTemplate.opsForValue().set(key,json);
            // 设置缓存期限
            stringRedisTemplate.expire(key,RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            unLock(lockKey);
        }
        return shop;
    }

    public Shop queryWithLogicalExpire(Long id) {
        // 根据id查询商铺
        // 1. 先在Redis缓存中进行查询
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        String jsonStr = stringRedisTemplate.opsForValue().get(key);
        // 1.1. 在Redis没有查询到(未命中，这种情况在开发中不会发生，因为会提前在缓存中设置好)
        if (jsonStr == null) {
            return null;
        }

        // 1.2 在Redis查询到,需要判断是否逻辑过期(读取到的数据是RedisData对应的串)
        RedisData redisData = JSONUtil.toBean(jsonStr, RedisData.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        JSONObject data = (JSONObject) redisData.getData();

//        LinkedHashMap dataMap = (LinkedHashMap) redisData.getData();
        // 这里不使用工具类只能自己将LinkedHashMap转为Shop对象了

        Shop shop = JSONUtil.toBean(data,Shop.class);

        // 1.2.1 如果逻辑期限在当前时间之后，说明当前数据逻辑有效，直接返回该数据
        if (expireTime.isAfter(LocalDateTime.now()))
        {
            return shop;
        }
        // 2. 当前数据逻辑过期了，需要重建缓存(重建缓存同样只需要一个去查询数据库并重建缓存)
        // 获取互斥锁
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        boolean successLock = tryLock(lockKey);

        // 3. 判断是否加锁，如果加锁了
        if (successLock)
        {
            // 在拿到锁后要double check，如果能从缓存拿到逻辑未过期数据，则无需重建缓存，直接解锁即可
            redisData = JSONUtil.toBean(jsonStr, RedisData.class);
            expireTime = redisData.getExpireTime();
            data = (JSONObject) redisData.getData();
            shop = JSONUtil.toBean(data,Shop.class);
            if (expireTime.isAfter(LocalDateTime.now()))
            {
                unLock(lockKey);         // 不加会导致锁无法释放，为了保证安全，最好还是给锁加一个expire
                return shop;
            }

            // 3.2 加锁成功且double check后仍然数据逻辑过期，开启另一个线程(使用一个线程池去做)查询数据库和重建缓存
            // 可以自己创建一个线程去做也行
            new Thread(() -> {
                    try {
                        rebuildRedis(id,20L);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    } finally {
                        unLock(lockKey);
                    }
                }).start();
            // 线程池的方式
//            CACHE_REBUILD_EXECUTOR.submit(() -> {
//                try {
//                    rebuildRedis(id,20L);
//                } catch (Exception e) {
//                    throw new RuntimeException(e);
//                } finally {
//                    unLock(lockKey);
//                }
//            });
        }

        // 返回旧数据(没有拿到锁、重建完数据后都返回的是旧数据)
        return shop;
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

    // 查询数据库并重建缓存
    public void rebuildRedis(Long id,Long expireSeconds){
        // 查询数据库对应的商店
        Shop shop = getById(id);

        // 构建要存入缓存的数据
        RedisData redisData = new RedisData();
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        redisData.setData(shop);

        // 将数据转为Json字符串并写入Redis缓存
        String json = JSONUtil.toJsonStr(redisData);
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        stringRedisTemplate.opsForValue().set(key,json);
    }


    // 自定义修改商铺方法，实现数据库和缓存的同步(使用事务控制保证同步)
    @Override
    @Transactional
    public Boolean update(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return null;
        }
        // 1. 先修改数据库
        boolean flag = updateById(shop);
        // 2. 删除Redis缓存
        if (flag)
            stringRedisTemplate.delete(RedisConstants.CACHE_SHOP_KEY + id);
        return flag;
    }
}
