package com.club.stub;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 短信验证码桩实现(设计说明书 1.1.2.1 非功能需求):
 *   验证码 5 分钟有效, 1 分钟内不可重复发送。
 * 真实短信通道接入时只需替换本类实现, 其余代码不动。
 */
@Component
public class SmsClient {

    private static final Logger log = LoggerFactory.getLogger(SmsClient.class);

    @Value("${club.sms.enabled:false}")
    private boolean enabled;

    /** mobile -> [code, 发送时间戳] */
    private final Map<String, String[]> store = new ConcurrentHashMap<>();

    /**
     * 发送验证码。返回 null 表示发送成功, 否则返回失败原因。
     */
    public String sendCaptcha(String mobile) {
        String[] prev = store.get(mobile);
        long now = System.currentTimeMillis();
        if (prev != null && now - Long.parseLong(prev[1]) < 60_000L) {
            return "发送过于频繁, 请 1 分钟后再试";
        }
        String code = String.valueOf((int) ((Math.random() * 9 + 1) * 100000));
        store.put(mobile, new String[]{code, String.valueOf(now)});
        // 桩: 验证码打到日志, 不真发短信
        log.info("[SMS STUB] 向 {} 发送验证码: {}", mobile, code);
        return null;
    }

    /**
     * 校验验证码。通过返回 true, 并清除记录(一次性)。
     */
    public boolean verify(String mobile, String code) {
        String[] rec = store.get(mobile);
        if (rec == null) {
            return false;
        }
        long sentAt = Long.parseLong(rec[1]);
        if (System.currentTimeMillis() - sentAt > 5 * 60_000L) {
            store.remove(mobile);
            return false;   // 过期
        }
        if (!rec[0].equals(code)) {
            return false;
        }
        store.remove(mobile);
        return true;
    }
}
