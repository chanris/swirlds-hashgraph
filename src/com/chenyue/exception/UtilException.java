package com.chenyue.exception;

public class UtilException extends RuntimeException{
    public UtilException() {
        super("工具类执行异常");
    }

    public UtilException(String msg) {
        super(msg);
    }
}
