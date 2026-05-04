package com.bioqc.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final AccessTokenBlacklistService accessTokenBlacklistService;

    public JwtAuthFilter(
        JwtTokenProvider jwtTokenProvider,
        AccessTokenBlacklistService accessTokenBlacklistService
    ) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.accessTokenBlacklistService = accessTokenBlacklistService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return "/actuator/health".equals(path)
            || "/api/auth/login".equals(path)
            || "/api/auth/refresh".equals(path)
            || "/api/auth/forgot-password".equals(path)
            || "/api/auth/reset-password".equals(path);
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            if (jwtTokenProvider.isAccessTokenValid(token)) {
                JwtTokenProvider.TokenDetails details = jwtTokenProvider.validateAccessToken(token);
                if (!accessTokenBlacklistService.isBlacklisted(details.tokenId())) {
                    UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                            details.username(),
                            null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + details.role()))
                        );
                    authentication.setDetails(details.userId());
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }
        }
        filterChain.doFilter(request, response);
    }
}
