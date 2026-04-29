package com.rostrlink.auth.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.rostrlink.redis.RedisSession;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    /**
     * Creates a Redis-specific JSON serializer with explicit type information.
     *
     * <p>
     * The {@link ObjectMapper} is built here rather than exposed as a
     * Spring {@code @Bean} so it is <em>never</em> auto-wired into Spring MVC's
     * HTTP message converters (which would break plain-JSON request-body parsing).
     *
     * <p>
     * Configuration decisions:
     * <ul>
     * <li>{@code JavaTimeModule} is registered explicitly — the no-arg
     * {@link GenericJackson2JsonRedisSerializer} does not reliably call
     * {@code findAndRegisterModules()} in all Spring Data Redis versions,
     * causing {@code Instant}/{@code OffsetDateTime} serialisation to fail.</li>
     * <li>{@code activateDefaultTypingAsProperty} with {@code "@class"} embeds
     * type info as a JSON property rather than a WRAPPER_ARRAY, so stored
     * values look like {@code {"@class":"com.rostrlink…RedisSession","userId":1,…}}
     * and can be round-tripped back to the correct Java type on read.</li>
     * <li>{@code WRITE_DATES_AS_TIMESTAMPS} is disabled so {@code Instant} fields
     * are stored as ISO-8601 strings, not epoch millis.</li>
     * </ul>
     */
    private static GenericJackson2JsonRedisSerializer redisJsonSerializer() {
        // Keep the mapper simple — no global activateDefaultTypingAsProperty.
        // Global NON_FINAL typing causes List/Map fields *inside* RedisSession to be
        // WRAPPER_ARRAY-wrapped (["java.util.ArrayList",[...]]), which BeanDeserializer
        // cannot reconstruct when reading back from Redis.
        // Top-level type info is handled by @JsonTypeInfo on RedisSession itself.
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return new GenericJackson2JsonRedisSerializer(mapper);
    }

    @Bean
    @Primary
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);

        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setValueSerializer(redisJsonSerializer());
        template.setHashKeySerializer(stringSerializer);
        template.setHashValueSerializer(redisJsonSerializer());
        template.afterPropertiesSet();
        return template;
    }

    @Bean
    public RedisTemplate<String, RedisSession> sessionRedisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, RedisSession> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);

        StringRedisSerializer keySerializer = new StringRedisSerializer();

        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES); // <-- add this

        Jackson2JsonRedisSerializer<RedisSession> valueSerializer =
                new Jackson2JsonRedisSerializer<>(mapper, RedisSession.class);

        template.setKeySerializer(keySerializer);
        template.setValueSerializer(valueSerializer);
        template.setHashKeySerializer(keySerializer);
        template.setHashValueSerializer(valueSerializer);

        template.afterPropertiesSet();
        return template;
    }
}
