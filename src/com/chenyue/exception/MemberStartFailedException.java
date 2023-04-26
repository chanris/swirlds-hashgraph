package com.chenyue.exception;

public class MemberStartFailedException extends RuntimeException{
    public MemberStartFailedException() {
        super();
    }

    public MemberStartFailedException(String message) {
        super(message);
    }
}
