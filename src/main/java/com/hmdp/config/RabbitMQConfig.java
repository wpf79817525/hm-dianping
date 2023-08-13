package com.hmdp.config;

import com.hmdp.utils.RabbitMQConstants;
import org.springframework.amqp.core.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {
    // 定义订单队列，当消息成为死信时，死信会根据RabbitMQConstants.DEAD_ORDER_ROUTING_KEY发送到死信交换机
    @Bean
    public Queue orderQueue() {
        return QueueBuilder.durable(RabbitMQConstants.ORDER_QUEUE_NAME).
                deadLetterExchange(RabbitMQConstants.DEAD_ORDER_EXCHANGE_NAME).
                deadLetterRoutingKey(RabbitMQConstants.DEAD_ORDER_ROUTING_KEY).
                build();
    }

    @Bean
    public Queue deadOrderQueue() {
        return QueueBuilder.
                durable(RabbitMQConstants.DEAD_ORDER_QUEUE_NAME).
                build();
    }

    @Bean
    public Exchange orderExchange() {
        return ExchangeBuilder.
                directExchange(RabbitMQConstants.ORDER_EXCHANGE_NAME).
                durable(true).
                build();
    }

    @Bean
    public Exchange deadOrderExchange() {
        return ExchangeBuilder.
                directExchange(RabbitMQConstants.DEAD_ORDER_EXCHANGE_NAME).
                durable(true).
                build();
    }

    @Bean
    public Binding orderBinding(@Qualifier("orderExchange") Exchange orderExchange,@Qualifier("orderQueue") Queue orderQueue) {
        return BindingBuilder.
                bind(orderQueue).
                to(orderExchange).
                with(RabbitMQConstants.ORDER_ROUTING_KEY).noargs();
    }

    @Bean
    public Binding deadOrderBinding(@Qualifier("deadOrderExchange") Exchange deadOrderExchange,@Qualifier("deadOrderQueue") Queue deadOrderQueue) {
        return BindingBuilder.
                bind(deadOrderQueue).
                to(deadOrderExchange).
                with(RabbitMQConstants.DEAD_ORDER_ROUTING_KEY).noargs();
    }
}
