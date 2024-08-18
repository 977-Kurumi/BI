package com.booooo.BIbackend.bizmq;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import org.springframework.boot.autoconfigure.amqp.ConnectionFactoryCustomizer;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

public class BiInitMain {

    public static void main(String[] args) {
        try {
            ConnectionFactory connectionFactory = new ConnectionFactory();
            connectionFactory.setHost("111.229.136.226");
            connectionFactory.setPort(5672);
            connectionFactory.setUsername("admin");
            connectionFactory.setPassword("2152698jp");
            Connection connection = connectionFactory.newConnection();
            Channel channel = connection.createChannel();
            String exchangeName = BiMqConstant.BI_EXCHANGE_NAME;
            channel.exchangeDeclare(exchangeName, "direct");
            String queueName = BiMqConstant.BI_QUEUE_NAME;
            channel.queueDeclare(queueName, true, false, false, null);

            channel.queueBind(queueName, exchangeName, BiMqConstant.BI_ROUTING_KEY);
        } catch (Exception e) {

        }
    }
}
