package com.mlbbroadcast.util;

import com.mlbbroadcast.configuration.DefaultProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@RequiredArgsConstructor
@Component
public class RedisUtilities {
    private final RedisTemplate<String, Object> redisTemplate;
    private final DefaultProperties configuration;
    private final ObjectMapper objectMapper;

    public void save(String key, Object value){
        redisTemplate.opsForValue().set(key, value, configuration.getCurrentPlay().getCurrentPlayCacheDuration());
    }

    public <T>T load(String key, Class<T> type){
        Object value = redisTemplate.opsForValue().get(key);
        if(value == null) return null;
        return objectMapper.convertValue(value, type);
    }

    public void remove(String key){
        redisTemplate.delete(key);
    }


    public boolean hasKey(String key){
        return redisTemplate.hasKey(key);
    }

}
