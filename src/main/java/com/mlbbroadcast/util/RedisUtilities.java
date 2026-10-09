package com.mlbbroadcast.util;

import com.mlbbroadcast.configuration.BusinessException;
import com.mlbbroadcast.configuration.DefaultProperties;
import com.mlbbroadcast.configuration.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;

@RequiredArgsConstructor
@Component
public class RedisUtilities {
    private final RedisTemplate<String, Object> redisTemplate;
    private final DefaultProperties configuration;
    private final ObjectMapper objectMapper;

    public void save(String key, Object value, Duration duration){
        redisTemplate.opsForValue().set(key, value, duration);


    }

    public Boolean setIfAbsent(String key, Object value, Duration duration){
        return redisTemplate.opsForValue().setIfAbsent(key, value, duration);

    }

    public <T>T load(String key, Class<T> type){
        Object value = redisTemplate.opsForValue().get(key);
        if(value == null) return null;
        return objectMapper.convertValue(value, type);
    }

    public void remove(String key){
        redisTemplate.delete(key);
    }
    public <T>T getAndDelete(String key, Class<T> type){
         Object value = redisTemplate.opsForValue().getAndDelete(key);
        if(value == null) return null;
        return objectMapper.convertValue(value, type);
    }


    public boolean hasKey(String key){
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    public Long expireDuration(String key){return redisTemplate.getExpire(key);}

}
