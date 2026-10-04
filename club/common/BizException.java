package com.club.common;

/**
 * 业务异常。message 直接展示给前端, httpStatus 固定 200,
 * 由 Result.code 表达错误类别(409 等), 便于前端统一处理。
 */
public class BizException extends RuntimeException {
    private final int code;

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    /** 常用: 名额不足 / 时段冲突 / 状态不允许 */
    public static BizException conflict(String message) {
        return new BizException(409, message);
    }

    public static BizException notFound(String message) {
        return new BizException(404, message);
    }

    public static BizException forbidden(String message) {
        return new BizException(403, message);
    }

    public static BizException badRequest(String message) {
        return new BizException(400, message);
    }

    /** 未登录 / 令牌失效(白名单路径上的写接口手动校验时使用) */
    public static BizException unauthorized(String message) {
        return new BizException(401, message);
    }

    public int getCode() { return code; }
}
