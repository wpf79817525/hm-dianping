package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.Voucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.*;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Autowired
    private ISeckillVoucherService seckillVoucherService;
    @Autowired
    private RedisIdWorker redisIdWorker;
    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private static final DefaultRedisScript<Long> SCRIPT;
    static {
        SCRIPT = new DefaultRedisScript();
        SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SCRIPT.setResultType(Long.class);
    }

    private IVoucherOrderService proxy;
    @Autowired
    private RabbitTemplate rabbitTemplate;

    // TODO 可以使用RabbitMQ进行处理
    /*
    @PostConstruct
    public void init() {
        new Thread(() -> {
            System.out.println("此处是异步线程，进行订单处理，预计使用rabbitmq来进行实现修改");
            while (true)
            {
                try {
                    // 1. 从消息队列中取出订单
                    List<MapRecord<String, Object, Object>> records = stringRedisTemplate.opsForStream().read(Consumer.from("group1", "consumer1"),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create("stream.orders", ReadOffset.lastConsumed()));
                    // 1.1 如果没取到消息
                    if (records == null || records.isEmpty())
                        continue;
                    // 2. 取到消息，将消息转化为实体类对象
                    MapRecord<String, Object, Object> message = records.get(0);
                    Map<Object, Object> voucherOrderMap = message.getValue();
                    VoucherOrder voucherOrder = new VoucherOrder();
                    voucherOrder.setId(Long.valueOf((String) voucherOrderMap.get("id")));
                    voucherOrder.setUserId(Long.valueOf((String) voucherOrderMap.get("userId")));
                    voucherOrder.setVoucherId(Long.valueOf((String) voucherOrderMap.get("voucherId")));
                    // 3. 开启数据库操作，注意必须使用代理对象的方法
                    voucherOrderHandler(voucherOrder);
                    // 4. 处理完消息后需要消费者组进行确认
                    stringRedisTemplate.opsForStream().acknowledge("stream.orders","group1",message.getId());
                } catch (Exception e) {
                    // 此处catch的话@Transactional会不会失效？(应该不会，对应的@Transactional方法的异常不由这捕获)
                    // 发生异常表示消息处理后未确认，需要重新获取该消息
                    log.error("异步线程发生异常",e);
                    while (true)
                    {
                        try {
                            // 1. 从消息队列中(pending-list)取出订单
                            List<MapRecord<String, Object, Object>> records = stringRedisTemplate.opsForStream().read(Consumer.from("group1", "consumer1"),
                                    StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                                    StreamOffset.create("stream.orders", ReadOffset.from("0")));
                            // 1.1 如果没有从pending-list没取到消息，说明全部处理，直接跳出循环
                            if (records == null || records.isEmpty())
                                break;
                            // 2. 取到消息，将消息转化为实体类对象
                            MapRecord<String, Object, Object> message = records.get(0);
                            Map<Object, Object> voucherOrderMap = message.getValue();
                            VoucherOrder voucherOrder = new VoucherOrder();
                            voucherOrder.setId(Long.valueOf((String) voucherOrderMap.get("id")));
                            voucherOrder.setUserId(Long.valueOf((String) voucherOrderMap.get("userId")));
                            voucherOrder.setVoucherId(Long.valueOf((String) voucherOrderMap.get("voucherId")));
                            // 3. 开启数据库操作，注意必须使用代理对象的方法
                            voucherOrderHandler(voucherOrder);
                            // 4. 处理完消息后需要消费者组进行确认
                            stringRedisTemplate.opsForStream().acknowledge("stream.orders","group1",message.getId());
                        } catch (InterruptedException ex) {
                            log.error("异步线程再次发生异常,处理pending-list异常",e);
                            try {
                                Thread.sleep(20);
                            } catch (InterruptedException exc) {
                                throw new RuntimeException(exc);
                            }
                        }
                    }
                }
            }
        }).start();
    }
     */
    //抢购限时优惠券
//    @Override
//    public Result buySeckillVoucher(Long voucherId) throws InterruptedException {
//        // 1. 根据voucherId查找限时优惠券
//        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
//        // 2. 查看优惠券是否过期
//        LocalDateTime beginTime = voucher.getBeginTime();
//        LocalDateTime endTime = voucher.getEndTime();
//        // 2.1 过期
//        if (beginTime.isAfter(LocalDateTime.now()))
//            return Result.fail("抢购未开始，敬请期待...");
//        if (endTime.isBefore(LocalDateTime.now()))
//            return Result.fail("抢购活动已结束...");
//        // 2.2 未过期
//        // 3. 查看库存
//        int stock = voucher.getStock();
//        // 3.1 库存不足
//        if (stock < 1)
//            return Result.fail("库存不足...");
//
//        // 针对一个用户，只有一个线程可以访问，但是其他用户仍然可以访问。
//        Long userId = UserHolder.getUser().getId();
//
////        ILock lock = new SimpleRedisLock(stringRedisTemplate,"order:" + userId.toString());
//        RLock lock = redissonClient.getLock("order:" + userId.toString());
//        boolean successLock = lock.tryLock(1, TimeUnit.SECONDS);
//        if (!successLock)
//            return Result.fail("请勿重复下单!!!");
////        synchronized (userId.toString().intern()){
////        }
//        try {
//            //这里必须使用代理对象的createSeckillVoucherOrder()方法，否则@Transactional失效
//            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
//            return proxy.createSeckillVoucherOrder(voucherId);
//        } finally {
//            lock.unlock();
//        }
//    }

    //抢购限时优惠券优化
    @Override
    public Result buySeckillVoucher(Long voucherId) throws InterruptedException {
        Long userId = UserHolder.getUser().getId();
        long orderId = redisIdWorker.nextId("order");
        // 1. 执行Lua脚本
        // TODO 在这里可以只使用Redis进行下单条件判断，符合条件将对应的订单发送到RabbitMQ即可
        long result = stringRedisTemplate.execute(SCRIPT, Collections.emptyList(), voucherId.toString(), userId.toString(),Long.toString(orderId));
        if (result != 0)
            return Result.fail(result == 1 ? "库存不足...":"请勿重复下单!!!");

        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setVoucherId(voucherId);
        voucherOrder.setId(orderId);
        voucherOrder.setUserId(userId);
        String orderStr = JSONUtil.toJsonStr(voucherOrder);
        //TODO 将订单消息存到消息队列
        rabbitTemplate.convertAndSend(RabbitMQConstants.ORDER_EXCHANGE_NAME,RabbitMQConstants.ORDER_ROUTING_KEY,orderStr);
        // 开启代理对象
        proxy = (IVoucherOrderService) AopContext.currentProxy();

        return Result.ok(orderId);
    }



//    @Transactional
//    public Result createSeckillVoucherOrder(Long voucherId) {
//        long userId = UserHolder.getUser().getId();
//        // 需要保证一人一单，要查询订单中是否包含userId对应的订单
//        int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
//        if (count > 0)
//            return Result.fail("该用户已经购买过一次了");
//
//        // 3.2 库存足够
//        // 4. 尝试更新库存(使用乐观锁解决超卖问题)
////        boolean success = seckillVoucherService.update().setSql("stock = stock - 1").eq("voucher_id",voucherId).gt("stock",0).update();
//        boolean success = seckillVoucherService.updateStock(voucherId);     // 自定义函数
//        if (!success)
//            return Result.fail("库存不足...");
//        // 5. 保存订单信息并返回订单id
//        VoucherOrder voucherOrder = new VoucherOrder();
//        long orderId = redisIdWorker.nextId("order");
//        voucherOrder.setId(orderId);
//        voucherOrder.setVoucherId(voucherId);
//        voucherOrder.setUserId(userId);
//        save(voucherOrder);
//
//        return Result.ok(orderId);
//    }

    @Transactional
    public void createSeckillVoucherOrder(VoucherOrder voucherOrder) {
        Long userId = voucherOrder.getUserId();
        Long voucherId = voucherOrder.getVoucherId();
        int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        if (count > 0)
        {
            log.error("一个用户仅允许下一单...");
            return;
        }
        // 1. 更新库存(使用乐观锁解决超卖问题)
        boolean success = seckillVoucherService.updateStock(voucherOrder.getVoucherId());     // 自定义函数
        if (!success)
        {
            log.error("库存不足...");
            return;
        }
        // 手动创建运行时异常，查看事务是否生效
//        int i = 1 / 0;
        // 2. 保存订单
        save(voucherOrder);
    }

    public void voucherOrderHandler(VoucherOrder voucherOrder) throws InterruptedException {
        // 其实不用加分布式锁，因为前面的Redis判断已经判断了一人一单，除此之外，由于数据库操作的线程只有一个，所以不存在并发问题，不需要加锁。
        Long userId = voucherOrder.getUserId();
        RLock lock = redissonClient.getLock("seckill:lock:" + userId);
        boolean isLock = lock.tryLock(1, TimeUnit.SECONDS);
        if (!isLock)
        {
            log.error("请勿重复下单......");
            return;
        }
        try {
            // 要使用代理对象的方法保证@Transactional的有效性
            proxy.createSeckillVoucherOrder(voucherOrder);
        } finally {
            lock.unlock();
        }
    }
}
