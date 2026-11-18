package com.example.notification_service.config;

import com.example.notification_service.notification.sse.NotificationEventSubscriber;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * FIX: previously wrapped NotificationEventSubscriber in a
 * MessageListenerAdapter(subscriber, "onMessage") constructed with plain
 * `new` inside this @Bean method — that adapter needs Spring's
 * afterPropertiesSet() lifecycle callback to resolve the method name into a
 * real invoker via reflection, which never ran since the adapter itself was
 * never registered as a bean. Result: NullPointerException the moment
 * Redis delivered an actual message. NotificationEventSubscriber now
 * implements MessageListener directly (see its own doc comment) and gets
 * registered as-is below — no adapter, no reflection, nothing to
 * misconfigure this way again.
 */
@Configuration
public class RedisConfig {

    @Bean
    public ChannelTopic notificationTopic(@Value("${app.notifications.redis-channel}") String channel) {
        return new ChannelTopic(channel);
    }

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            NotificationEventSubscriber subscriber,
            ChannelTopic notificationTopic) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(subscriber, notificationTopic);
        return container;
    }

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }
}