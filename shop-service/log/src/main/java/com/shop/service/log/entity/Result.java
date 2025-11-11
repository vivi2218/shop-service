package com.shop.service.log.entity;

import com.baomidou.mybatisplus.annotation.TableField;

import java.io.Serializable;

public class Result implements Serializable {
    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
    private int code;
    private Object data;
    private String msg;
    
    public Result(int code, Object data, String msg){
        this.code = code;
        this.data = data;
        this.msg = msg;
    }
    
    public static Result end(int code, Object data, String msg){
        return new Result(code, data, msg);
    }
    
    public int getCode() {
        return code;
    }
    
    public void setCode(int code) {
        this.code = code;
    }
    
    public Object getData() {
        return data;
    }
    
    public void setData(Object data) {
        this.data = data;
    }
    
    public String getMsg() {
        return msg;
    }
    
    public void setMsg(String msg) {
        this.msg = msg;
    }
}
