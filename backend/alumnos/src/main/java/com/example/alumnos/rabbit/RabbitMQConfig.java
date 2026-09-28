package com.example.alumnos.rabbit;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE = "microservicios.exchange";
    public static final String COLA_NOTIFICACIONES = "microservicios.cola.alumnos";

    @Bean
    public TopicExchange microserviciosExchange() {
        return new TopicExchange(EXCHANGE);
    }

    @Bean
    public Queue colaNotificaciones() {
        return new Queue(COLA_NOTIFICACIONES, true);
    }

    @Bean
    public Binding binding(Queue colaNotificaciones, TopicExchange microserviciosExchange) {
        return BindingBuilder.bind(colaNotificaciones).to(microserviciosExchange).with("docente.*");
    }

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}