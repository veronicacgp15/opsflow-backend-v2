package com.opsflow.auth_service.infrastructure.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static com.opsflow.auth_service.domain.constants.AuthConstants.*;

@Configuration
public class RabbitMQConfig {

    @Bean
    public DirectExchange authExchange() {
        return new DirectExchange(AUTH_EXCHANGE);
    }

    @Bean
    public Queue userRegisteredQueue() {
        return new Queue(USER_REGISTERED_QUEUE, true);
    }

    @Bean
    public Binding bindingAuth(Queue userRegisteredQueue, DirectExchange authExchange) {
        return BindingBuilder.bind(userRegisteredQueue).to(authExchange).with(USER_REGISTERED_ROUTING_KEY);
    }

    @Bean
    public Queue authQueue() {
        return new Queue(AUTH_USER_REGISTERED_QUEUE, true);
    }

    @Bean
    public TopicExchange opsflowExchange() {
        return new TopicExchange(OPSFLOW_EXCHANGE);
    }

    @Bean
    public Binding bindingOpsflow(Queue authQueue, TopicExchange opsflowExchange) {
        return BindingBuilder.bind(authQueue).to(opsflowExchange).with(AUTH_USER_ROUTING_KEY);
    }

    @Bean
    public Jackson2JsonMessageConverter producerJackson2MessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
