package com.bioqc.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class TokenCookieService {

    private final String refreshCookieName;
    private final String refreshCookiePath;
    private final String refreshCookieDomain;
    private final boolean refreshCookieSecure;
    private final String refreshCookieSameSite;

    public TokenCookieService(
        @Value("${app.auth.refresh-cookie.name:refresh_token}") String refreshCookieName,
        @Value("${app.auth.refresh-cookie.path:/api/auth}") String refreshCookiePath,
        @Value("${app.auth.refresh-cookie.domain:}") String refreshCookieDomain,
        @Value("${app.auth.refresh-cookie.secure:false}") boolean refreshCookieSecure,
        @Value("${app.auth.refresh-cookie.same-site:Lax}") String refreshCookieSameSite
    ) {
        this.refreshCookieName = refreshCookieName;
        this.refreshCookiePath = refreshCookiePath;
        this.refreshCookieDomain = refreshCookieDomain;
        this.refreshCookieSecure = refreshCookieSecure;
        this.refreshCookieSameSite = refreshCookieSameSite;
    }

    public String createRefreshCookieHeader(String refreshToken, long maxAgeSeconds) {
        return HttpHeaders.SET_COOKIE + ": " + buildCookie(refreshToken, maxAgeSeconds, true).toString();
    }

    public String clearRefreshCookieHeader() {
        return HttpHeaders.SET_COOKIE + ": " + buildCookie("", 0, true).toString();
    }

    public String createRefreshCookieValue(String refreshToken, long maxAgeSeconds) {
        return buildCookie(refreshToken, maxAgeSeconds, true).toString();
    }

    public String clearRefreshCookieValue() {
        return buildCookie("", 0, true).toString();
    }

    public String resolveRefreshToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (refreshCookieName.equals(cookie.getName()) && StringUtils.hasText(cookie.getValue())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private ResponseCookie buildCookie(String value, long maxAgeSeconds, boolean httpOnly) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(refreshCookieName, value)
            .httpOnly(httpOnly)
            .secure(refreshCookieSecure)
            .sameSite(refreshCookieSameSite)
            .path(refreshCookiePath)
            .maxAge(maxAgeSeconds);

        if (StringUtils.hasText(refreshCookieDomain)) {
            builder.domain(refreshCookieDomain);
        }

        return builder.build();
    }
}
