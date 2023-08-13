package com.hmdp;

import com.hmdp.entity.Shop;
import com.hmdp.entity.Voucher;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherService;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.service.impl.VoucherServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.SHOP_GEO_KEY;
import static com.hmdp.utils.RedisConstants.USER_SIGN_KEY;


@SpringBootTest
class HmDianPingApplicationTests {

    @Autowired
    ShopServiceImpl shopService;
    @Autowired
    RedisIdWorker redisIdWorker;

    @Autowired
    IVoucherService iVoucherService;
    @Autowired
    VoucherServiceImpl voucherServiceImpl;

    @Autowired
    StringRedisTemplate stringRedisTemplate;

    @Test
    public void test1() {
        shopService.rebuildRedis(1L,30L);
    }

    @Test
    public void testRedisWorkerNextId() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(300);
        Runnable task = () -> {
            for (int i = 0;i < 100;i++) {
                System.out.println(redisIdWorker.nextId("test"));
            }
            latch.countDown();
        };
        long start = System.currentTimeMillis();
        for (int i = 0; i < 300; i++) {
            new Thread(task).start();
        }
        latch.await();
        long end = System.currentTimeMillis();
        System.out.println("time=" + (end - start));
    }

    @Test
    public void test2() {
        shopService.queryWithLogicalExpire(1L);
    }

    @Test
    public void testMultiThread() {
        Runnable task = () -> {
            for (int i = 0;i < 6;i++)
                System.out.println(redisIdWorker);
        };
        for (int i = 0; i < 3; i++) {
            new Thread(task).start();
        }
        System.out.println(77777);
    }

    @Test
    public void testProxy() {
        Voucher voucher = new Voucher();
        voucher.setShopId(1L);
        voucher.setTitle("测试111");
        voucher.setSubTitle("测试111");
        voucher.setRules("测试111");
        voucher.setPayValue(100L);
        voucher.setActualValue(100L);
        voucher.setType(1);
        voucher.setStatus(1);
        voucher.setStock(100);
        voucher.setBeginTime(LocalDateTime.of(2023,1,1,0,0));
        voucher.setEndTime(LocalDateTime.of(2023,1,15,0,0));
        voucher.setBeginTime(LocalDateTime.of(2023,1,1,0,0));
        voucher.setBeginTime(LocalDateTime.of(2023,1,1,0,0));
        voucherServiceImpl.addSeckillVoucher(voucher);
    }

    @Test
    public void testSeckillVoucherService(@Autowired ISeckillVoucherService seckillVoucherService){
        boolean b = seckillVoucherService.updateStock(14L);
        System.out.println(b);
    }

    @Test
    public void testRedissonLock(@Autowired RedissonClient redissonClient) throws InterruptedException {
        // 测试watchDog机制
        RLock lock = redissonClient.getLock("lock");
        boolean isLock = lock.tryLock(1, TimeUnit.SECONDS);
        System.out.println(Thread.currentThread().getId());
        if (isLock)
        {
            try {
                System.out.println("获取锁成功，正在执行业务...");
            } finally {
                lock.unlock();
            }
        }
    }

    @Test
    public void setShopGEO() {
        // TODO 将Shop存储到Redis中(key:typeId member:shopId 经纬度：x/y)
        // 1. 获取所有商店Shop
        List<Shop> shops = shopService.list();
        // 2. 将商店按照类型进行分类(按照typeId进行分类)
        Map<Long, List<Shop>> map = shops.stream().collect(Collectors.groupingBy(shop -> shop.getTypeId()));
        // 3. 遍历所有商店类型
        for (Map.Entry<Long,List<Shop>> entry: map.entrySet())
        {
            Long typeId = entry.getKey();
            List<Shop> shopList = entry.getValue();
            String key = SHOP_GEO_KEY + typeId;
            List<RedisGeoCommands.GeoLocation<String>> members = new ArrayList<>(shopList.size());
            // 4. 遍历商店类型对应的列表，将shopId,x,y写入Redis的GEO
            for (Shop shop : shopList) {
                members.add(new RedisGeoCommands.GeoLocation<>(shop.getId().toString(),new Point(shop.getX(),shop.getY())));
            }
            stringRedisTemplate.opsForGeo().add(key,members);
        }
    }

    @Test
    public void testSetBitMap() {
        // 1. 获取当前用户
        Long userId = 1008L;
        // 2. 获取今天的时间
        LocalDateTime time = LocalDateTime.now();
        String format = time.format(DateTimeFormatter.ofPattern(":yyyy:MM"));
        int dayOfMonth = time.getDayOfMonth();
        String key = USER_SIGN_KEY + userId + format;
        stringRedisTemplate.opsForValue().setBit(key,12,false);
    }

    @Test
    public void testHyperLogLog() {
        String[] users = new String[1000];
        for (int i = 0;i < 1000000;i++)
        {
            users[i % 1000] = "user_" + i;
            if (i % 1000 == 999)
            {
                stringRedisTemplate.opsForHyperLogLog().add("UV",users);
            }
        }
        Long count = stringRedisTemplate.opsForHyperLogLog().size("UV");
        System.out.println(count);
    }

    @Test
    public void testIncrement() {
        Long count = stringRedisTemplate.opsForValue().increment("testKey");
        System.out.println(count);
    }
}
