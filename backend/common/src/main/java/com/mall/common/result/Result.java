package com.mall.common.result;

import lombok.Data;

import java.io.Serializable;

/**
 * 全系统统一返回体。
 * 为什么统一：网关/前端只需要按一种格式解析；Feign 调用方也能用 code != 200 快速判断业务失败。
 */
@Data
public class Result<T> implements Serializable {

    /** 业务状态码：200 成功，401 未登录/Token 无效，其它为业务失败 */
    private int code;
    private String msg;
    private T data;

    public static <T> Result<T> ok(T data) {
        Result<T> r = new Result<>();
        r.setCode(200);
        r.setMsg("success");
        r.setData(data);
        return r;
    }

    public static <T> Result<T> ok() {
        return ok(null);
    }

    public static <T> Result<T> fail(String msg) {
        return fail(500, msg);
    }

    public static <T> Result<T> fail(int code, String msg) {
        Result<T> r = new Result<>();
        r.setCode(code);
        r.setMsg(msg);
        return r;
    }

    public static <T> Result<T> unauthorized(String msg) {
        return fail(401, msg);
    }
}
