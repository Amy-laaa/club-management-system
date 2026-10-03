package com.club.security;

/**
 * 当前登录用户上下文。AuthInterceptor 校验通过后写入,
 * Service / Controller 通过它获取当前用户, 不再从请求里手动解析 JWT。
 */
public class UserContext {

    private static final ThreadLocal<CurrentUser> HOLDER = new ThreadLocal<>();

    public static void set(CurrentUser user) {
        HOLDER.set(user);
    }

    public static CurrentUser get() {
        return HOLDER.get();
    }

    public static Long userId() {
        CurrentUser u = HOLDER.get();
        return u == null ? null : u.getUserId();
    }

    public static String roleCode() {
        CurrentUser u = HOLDER.get();
        return u == null ? null : u.getRoleCode();
    }

    public static void clear() {
        HOLDER.remove();
    }

    /**
     * 要求已登录, 否则抛 401。
     * 用于"读匿名、写需登录"的接口(如 POST /api/clubs):
     * 这类路径在拦截器白名单里, 需要在方法内手动校验。
     */
    public static CurrentUser require() {
        CurrentUser u = HOLDER.get();
        if (u == null) {
            throw new com.club.common.BizException(401, "请先登录");
        }
        return u;
    }

    public static class CurrentUser {
        private Long userId;
        private String studentNo;
        private String roleCode;

        public CurrentUser(Long userId, String studentNo, String roleCode) {
            this.userId = userId;
            this.studentNo = studentNo;
            this.roleCode = roleCode;
        }

        public Long getUserId() { return userId; }
        public String getStudentNo() { return studentNo; }
        public String getRoleCode() { return roleCode; }
    }
}
