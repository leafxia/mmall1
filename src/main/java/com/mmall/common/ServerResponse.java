package com.mmall.common;

import org.codehaus.jackson.annotate.JsonIgnore;
import org.codehaus.jackson.map.annotate.JsonSerialize;

import java.io.Serializable;

/**
 * @author leaf
 * @create 2018-11-10 9:52
 */
@JsonSerialize(include =JsonSerialize.Inclusion.NON_NULL )
//保证序列化JSON的时候如果是NULL的对象KEY也会消失
public class ServerResponse<T> implements Serializable {

    private int status;//状态
    private String msg;//数据
    private T data;//对象

    private ServerResponse(int status){
        this.status=status;
    }

    private ServerResponse(int status,T data){
        this.status=status;
        this.data=data;
    }

    private ServerResponse(int status,String msg,T data){
        this.status=status;
        this.msg=msg;
        this.data=data;
    }
    private ServerResponse(int status,String msg){
        this.status=status;
        this.msg=msg;
    }

    /**
     * 序列化后不会显示在json里面
     * 成功
     * @return
     */
    @JsonIgnore
    public  boolean isSuccess(){
        return  this.status==ResponseCode.SUCCESS.getCode();
    }

    public int getStatus(){
        return status;
    }
    public T getData(){
        return  data;
    }

    public  String getMsg(){
        return  msg;
    }

    /**
     * 返回成功状态
     * @param <T>
     * @return
     */
    public  static  <T> ServerResponse<T> createBySuccess(){
    return  new ServerResponse<T>(ResponseCode.SUCCESS.getCode());
    }

    /**
     * 返回成功的状态并携带数据
     * @param msg
     * @param <T>
     * @return
     */
    public  static  <T> ServerResponse<T> createBySuccessMessage(String msg){
        return  new ServerResponse<T>(ResponseCode.SUCCESS.getCode(),msg);
    }

    /**
     *返回成功状态并返回对象
     * @param data
     * @param <T>
     * @return
     */
    public  static  <T> ServerResponse<T> createBySuccess(T data){
        return  new ServerResponse<T>(ResponseCode.SUCCESS.getCode(),data);
    }

    /**
     * 返回成功状态并返回对象及数据
     * @param msg
     * @param data
     * @param <T>
     * @return
     */
    public  static  <T> ServerResponse<T> createBySuccess(String msg ,T data){
        return  new ServerResponse<T>(ResponseCode.SUCCESS.getCode(),msg,data);
    }

    /**
     * 返回一个错误
     * @param <T>
     * @return
     */
    public  static  <T> ServerResponse<T> createByError(){
        return  new ServerResponse<T>(ResponseCode.ERROR.getCode(),ResponseCode.ERROR.getDesc());
    }

    public  static  <T> ServerResponse<T> createByErrorMessage(String errorMessage){
        return  new ServerResponse<T>(ResponseCode.ERROR.getCode(),errorMessage);
    }

    public  static  <T> ServerResponse<T> createByErrorCodeMessage(int errorCode,String errorMessage){
        return  new ServerResponse<T>(errorCode,errorMessage);
    }
}
