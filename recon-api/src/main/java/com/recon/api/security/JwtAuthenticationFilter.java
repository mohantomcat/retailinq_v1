package com.recon.api.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter
        extends OncePerRequestFilter {

    private final JwtTokenProvider tokenProvider;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        String token = extractToken(request);

        if (StringUtils.hasText(token)
                && tokenProvider.validateToken(token)) {
            try {
                String userId =
                        tokenProvider.getUserIdFromToken(token);
                Set<String> permissions =
                        tokenProvider.getPermissionsFromToken(token);
                Set<String> storeIds =
                        tokenProvider.getStoreIdsFromToken(token);
                String tenantId =
                        tokenProvider.getTenantIdFromToken(token);

                var authorities = permissions.stream()
                        .map(p -> new SimpleGrantedAuthority(
                                "PERM_" + p))
                        .collect(Collectors.toList());

                ReconUserPrincipal principal =
                        new ReconUserPrincipal(
                                userId, tenantId,
                                permissions, storeIds);

                var auth =
                        new UsernamePasswordAuthenticationToken(
                                principal, null, authorities);

                SecurityContextHolder.getContext()
                        .setAuthentication(auth);

            } catch (Exception e) {
                log.error("Auth error: {}", e.getMessage());
            }
        }

        filterChain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        if (StringUtils.hasText(bearer)
                && bearer.startsWith("Bearer ")) {
            return bearer.substring(7);
        }
        return null;
    }
}