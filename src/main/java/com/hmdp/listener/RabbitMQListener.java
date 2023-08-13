package com.hmdp.listener;

import cn.hutool.json.JSONUtil;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.IVoucherOrderService;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;


@Component
public class RabbitMQListener {
    @Autowired
    private IVoucherOrderService voucherOrderService;
    @RabbitListener(queues = "order_queue")
    public void consumeVoucherOrder(Message message) {
        String orderStr = new String(message.getBody());
        VoucherOrder voucherOrder = JSONUtil.toBean(orderStr, VoucherOrder.class);
        voucherOrderService.createSeckillVoucherOrder(voucherOrder);
    }
}
