package com.recon.api.security;

import com.recon.api.service.TenantApiKeyAuthService;
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
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private final TenantApiKeyAuthService tenantApiKeyAuthService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            String apiKeyValue = request.getHeader("X-API-Key");
            if (StringUtils.hasText(apiKeyValue)) {
                try {
                    ReconUserPrincipal principal = tenantApiKeyAuthService.authenticate(apiKeyValue);
                    if (principal != null) {
                        var authorities = principal.getPermissions().stream()
                                .map(permission -> new SimpleGrantedAuthority("PERM_" + permission))
                                .collect(Collectors.toList());
                        SecurityContextHolder.getContext().setAuthentication(
                                new UsernamePasswordAuthenticationToken(principal, null, authorities)
                        );
                    }
                } catch (Exception ex) {
                    log.warn("API key auth failed: {}", ex.getMessage());
                }
            }
        }

        filterChain.doFilter(request, response);
    }
}
