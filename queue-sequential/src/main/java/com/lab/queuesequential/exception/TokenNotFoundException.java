package com.lab.queuesequential.exception;

public class TokenNotFoundException extends RuntimeException {

    public TokenNotFoundException(String token) {
        super("대기열에 존재하지 않는 토큰이거나 이미 처리된 토큰입니다: " + token);
    }
}
