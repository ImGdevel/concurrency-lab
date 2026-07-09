package com.lab.queuesequential.exception;

public class NotEnterableException extends RuntimeException {

    public NotEnterableException(long rank) {
        super("아직 입장 순번이 아닙니다. 현재 순번: " + rank);
    }
}
