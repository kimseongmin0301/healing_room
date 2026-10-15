package com.healingroom.global.config;

import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import com.healingroom.domain.chat.repository.ChatRepository;
import com.healingroom.domain.chat.service.ChatService;

/** Redis pub/sub 구독: 채팅 채널({prefix}chat)에 새 메시지가 오면 이 서버의 방 사람들에게 보낸다 */
@Configuration
public class RedisConfig {

    private static final Logger log = LoggerFactory.getLogger(RedisConfig.class);

    @Bean
    public RedisMessageListenerContainer redisListenerContainer(RedisConnectionFactory connectionFactory,
                                                                ChatRepository chatRepository,
                                                                ChatService chatService) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener((message, pattern) -> {
            try {
                chatService.onPublished(new String(message.getBody(), StandardCharsets.UTF_8));
            } catch (RuntimeException e) {
                log.warn("채팅 메시지 전달 실패: {}", e.getMessage());
            }
        }, new ChannelTopic(chatRepository.channel()));
        return container;
    }
}
