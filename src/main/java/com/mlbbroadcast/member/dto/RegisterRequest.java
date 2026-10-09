package com.mlbbroadcast.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;


public record RegisterRequest(
        @NotBlank String registerToken,
        @NotBlank
        @Size(min = 4, max = 10)
        @Pattern(regexp = "^[가-힣a-zA-Z0-9]+$")
        String nickName
) {}