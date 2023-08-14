package com.hmdp.listener;

import cn.hutool.json.JSONUtil;
import com.hmdp.MyException.OrderConsumeException;
import com.hmdp.entity.VoucherErrorOrder;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.IVoucherErrorOrderService;
import com.hmdp.service.IVoucherOrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.rabbitmq.client.Channel;

import java.io.IOException;


@Component
@Slf4j
public class RabbitMQListener {
    @Autowired
    private IVoucherOrderService voucherOrderService;
    @Autowired
    private IVoucherErrorOrderService voucherErrorOrderService;
    @RabbitListener(queues = "order_queue",ackMode = "MANUAL")
    public void consumeVoucherOrder(Message message, Channel channel) throws Exception{
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            String orderStr = new String(message.getBody());
            VoucherOrder voucherOrder = JSONUtil.toBean(orderStr, VoucherOrder.class);
            voucherOrderService.createSeckillVoucherOrder(voucherOrder);
            // 业务处理成功，返回ACK
            channel.basicAck(deliveryTag,false);
        } catch (Exception e) {
            // 业务出现异常，将消息放到死信队列
            channel.basicNack(deliveryTag,false,false);
            // 抛出异常进行观察，这里抛出运行时异常和非运行时异常都可以
            throw new OrderConsumeException("数据库操作订单出现异常...",e);
        }
    }

    // 开启对死信队列的监听，将对应的死信(出现异常的订单消息)再次进行处理，如果处理失败，则将其记录到数据库当中
    // 这里第一个try中没有手动ACK，无论死信队列是否正常被业务进行处理，都将消息从死信队列移除，除非保存到异常订单数据库时出错
    @RabbitListener(queues = "dead_order_queue",ackMode = "MANUAL")
    public void consumeDeadVoucherOrder(Message message,Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            String orderStr = new String(message.getBody());
            VoucherOrder voucherOrder = JSONUtil.toBean(orderStr, VoucherOrder.class);
            voucherOrderService.createSeckillVoucherOrder(voucherOrder);
            channel.basicAck(deliveryTag,false);
        } catch (Exception e) {
            // 从死信队列取出的消息仍然出现异常，将消息记录到异常订单库当中
            try {
                String orderStr = new String(message.getBody());
                VoucherErrorOrder voucherOrder = JSONUtil.toBean(orderStr, VoucherErrorOrder.class);
                // 需要注意的是，异常订单存储不会消耗实际库存
                voucherErrorOrderService.save(voucherOrder);
                log.info("异常订单已经成功存入数据库...");
                channel.basicAck(deliveryTag,false);
            } catch (Exception ex) {
                // 为防止异常订单处理失败丢失，只能让其不断重回死信队列(发生概率比较低)
                channel.basicNack(deliveryTag,false,true);
                throw new RuntimeException("异常订单处理出现异常...请查看死信队列...",ex);
            }
            // TODO 将该消息写到异常订单数据库中
            throw new RuntimeException("订单处理再次出现异常...请查看数据库异常订单表",e);
        }
    }
}
