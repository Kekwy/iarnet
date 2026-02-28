package com.kekwy.iarnet.model;

/**
 * Web API 统一返回类型。
 *
 * @param <T> 业务数据泛型，无数据时使用 {@link Void}
 */
public class Response<T> {

    /** 业务状态码，200 表示成功 */
    private int code;
    /** 提示信息 */
    private String message;
    /** 业务数据 */
    private T data;

    public Response() {
    }

    public Response(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    // ---------- 静态工厂 ----------

    private static final int CODE_OK = 200;
    private static final int CODE_ERROR = 500;

    /** 成功，带数据 */
    public static <T> Response<T> ok(T data) {
        return new Response<>(CODE_OK, "success", data);
    }

    /** 成功，无数据 */
    public static <T> Response<T> ok() {
        return ok(null);
    }

    /** 成功，自定义提示 */
    public static <T> Response<T> ok(String message, T data) {
        return new Response<>(CODE_OK, message != null ? message : "success", data);
    }

    /** 失败，默认 500 */
    public static <T> Response<T> fail(String message) {
        return new Response<>(CODE_ERROR, message, null);
    }

    /** 失败，指定状态码 */
    public static <T> Response<T> fail(int code, String message) {
        return new Response<>(code, message, null);
    }
}
