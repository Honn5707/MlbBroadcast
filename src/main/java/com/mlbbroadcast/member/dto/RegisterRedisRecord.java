package com.mlbbroadcast.member.dto;
import com.mlbbroadcast.member.Provider;

public record RegisterRedisRecord(
        String sub, Provider provider
) {
}
