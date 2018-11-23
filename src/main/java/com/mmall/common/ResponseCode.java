package com.mmall.common;

/**
 * 响应编码枚举类
 * @author leaf
 * @create 2018-11-09 20:35
 */
public enum  ResponseCode {

    SUCCESS(0,"SUCCESS"),
    ERROR(1,"ERROR"),
    NEED_LOGIN(10,"NEED_LOGIN"),//响应给予需要登陆
    ILLEGAL_ARGUMENT(2,"ILLEGAL_ARGUMENT");

    private final int code;
    private final  String desc;

    ResponseCode(int code,String desc){
        this.code=code;
        this.desc = desc;
    }

    public int getCode(){
        return  code;
    }

    public String getDesc(){
        return  desc;
    }
}
