package com.hmdp.listener;

import cn.hutool.json.JSONUtil;
import com.hmdp.MyException.OrderConsumeException;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.IVoucherOrderService;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.rabbitmq.client.Channel;


@Component
public class RabbitMQListener {
    @Autowired
    private IVoucherOrderService voucherOrderService;
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
            // 抛出异常保证事务的回滚，这里抛出运行时异常和非运行时异常都可以
            throw new OrderConsumeException("数据库操作订单出现异常...",e);
        }
    }

    // 暂时关闭对死信队列的监听
//    @RabbitListener(queues = "dead_order_queue")
//    public void consumeDeadVoucherOrder(Message message) {
//        String orderStr = new String(message.getBody());
//        VoucherOrder voucherOrder = JSONUtil.toBean(orderStr, VoucherOrder.class);
//        voucherOrderService.createSeckillVoucherOrder(voucherOrder);
//    }
}
