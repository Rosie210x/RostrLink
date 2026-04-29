package com.rostrlink.util;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.util.Arrays;
import java.util.Optional;

@Component
public class CookieUtil {

    public static final String SESSION_COOKIE = "SESSION_ID";

    @Value("${server.servlet.session.cookie.secure:true}")
    private boolean secure;

    @Value("${server.servlet.session.cookie.same-site:Strict}")
    private String sameSite;

    @Value("${app.security.session.ttl-seconds:3600}")
    private int defaultMaxAge;

    /**
     * Write the session cookie with security attributes.
     */
    public void writeSessionCookie(HttpServletResponse response,
                                   String sessionId,
                                   int maxAgeSeconds) {
        // Single Set-Cookie header with all attributes including SameSite
        String cookieValue = String.format(
                "%s=%s; Path=/; HttpOnly; Max-Age=%d; SameSite=%s%s",
                SESSION_COOKIE,
                sessionId,
                maxAgeSeconds,
                sameSite,
                secure ? "; Secure" : ""
        );
        response.addHeader("Set-Cookie", cookieValue);
    }

    public void clearSessionCookie(HttpServletResponse response) {
        Cookie cookie = new Cookie(SESSION_COOKIE, "");
        cookie.setHttpOnly(true);
        cookie.setSecure(secure);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
    }

    public Optional<String> read(HttpServletRequest request, String name) {
        if (request.getCookies() == null) return Optional.empty();
        return Arrays.stream(request.getCookies())
                .filter(c -> name.equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst();
    }
}