package com.rostrlink.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.security")
public class AppProperties {

    private Session session = new Session();
    private Login login = new Login();
    private Otp otp = new Otp();

    @Data
    public static class Session {
        private long ttlSeconds = 3600;
        private long kioskTtlSeconds = 28800;
        private int maxConcurrent = 5;
    }

    @Data
    public static class Login {
        private int maxAttempts = 5;
        private int lockoutMinutes = 15;
        private int rateLimitWindowSeconds = 60;
        private int rateLimitIpMax = 20;
        private int rateLimitUserMax = 10;
    }

    @Data
    public static class Otp {
        private int ttlMinutes = 10;
        private int maxSendsPerHour = 3;
        private int length = 6;
    }
}