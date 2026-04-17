package com.dentalManagement.dentalFlowBackend.config;

import com.dentalManagement.dentalFlowBackend.service.SseEventSubscriber;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    /**
     * RedisTemplate<String, String> — used by SseEventPublisher to publish
     * JSON payloads onto "lab:{labId}" and "user:{userId}" channels.
     *
     * Both keys and values are plain UTF-8 strings (no Java serialization).
     */
    @Bean
    public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setValueSerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);
        template.setHashValueSerializer(stringSerializer);
        template.afterPropertiesSet();
        return template;
    }

    /**
     * RedisMessageListenerContainer — subscribes to all "lab:*" and "user:*"
     * pattern channels and hands every incoming message to SseEventSubscriber.
     *
     * Pattern subscription means a single container handles all labs and all
     * users without needing to subscribe/unsubscribe as clients connect.
     */
    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            SseEventSubscriber sseEventSubscriber) {

        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);

        // Subscribe to ALL lab channels and ALL user channels in one shot
        container.addMessageListener(sseEventSubscriber, new PatternTopic("lab:*"));
        container.addMessageListener(sseEventSubscriber, new PatternTopic("user:*"));

        return container;
    }
}
