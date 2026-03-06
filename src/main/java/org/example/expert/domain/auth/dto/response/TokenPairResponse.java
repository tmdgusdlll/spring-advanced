package org.example.expert.domain.auth.dto.response;

import lombok.Getter;

@Getter
public class TokenPairResponse {

    private final String accessToken;
    private final String refreshToken;

    public TokenPairResponse(String accessToken, String refreshToken) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
    }
}
