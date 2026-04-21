package com.rostrlink.auth.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    /*creates and configures a JSON converter object that will be reused throughout the entire application
    * input: none
    * output: an ObjectMapper object that knows how to convert Java objects to JSON and back
    * everything that talks to Redis needs to be able to convert to and from JSON
    */
    @Bean //create once and reuse
    public ObjectMapper redisObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        /*
        - By default, ObjectMapper doesnt understand Java's LocalDateTime and LocalDate classes.
        - registerModule() teaches ObjectMapper how to serialize and deserialize them.
        - For e.g. LocalDateTime.now() -> "2026-04-20T18:30:00"
        - Goal: store sessions in Redis with timestamps without errors
         */
        mapper.registerModule(new JavaTimeModule());
        /*
        - Tells ObjectMapper not to write dates as timestamps
        - input: SerializationFeature.WRITE_DATES_AS_TIMESTAMPS acts as a setting flag
        - output: ObjectMapper will write dates as ISO-8601 strings
        - Goal: without this setting, dates would become 1713605400000
        - With this, we get "2026-04-20T18:30:00" instead for user login time for e.g.
        - Contribution: Logs and debugging become much easier to read
         */
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory,
                                                       ObjectMapper redisObjectMapper) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);

        GenericJackson2JsonRedisSerializer jsonSerializer =
                new GenericJackson2JsonRedisSerializer(redisObjectMapper);
        StringRedisSerializer stringSerializer = new StringRedisSerializer();

        template.setKeySerializer(stringSerializer);
        template.setValueSerializer(jsonSerializer);

        template.setHashKeySerializer(stringSerializer);
        template.setHashValueSerializer(jsonSerializer);
        template.afterPropertiesSet();
        return template;
    }
}