package com.hmdp.config;

import com.hmdp.utils.RabbitMQConstants;
import org.springframework.amqp.core.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {
    @Bean
    public Queue orderQueue() {
        return QueueBuilder.durable(RabbitMQConstants.ORDER_QUEUE_NAME).build();
    }

    @Bean
    public Exchange orderExchange() {
        return ExchangeBuilder.directExchange(RabbitMQConstants.ORDER_EXCHANGE_NAME).durable(true).build();
    }

    @Bean
    public Binding orderBinding(@Qualifier("orderExchange") Exchange orderExchange,@Qualifier("orderQueue") Queue orderQueue) {
        return BindingBuilder.bind(orderQueue).to(orderExchange).with(RabbitMQConstants.ORDER_ROUTING_KEY).noargs();
    }
}
