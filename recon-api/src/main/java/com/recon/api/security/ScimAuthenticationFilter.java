package com.recon.api.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recon.api.domain.ScimErrorResponse;
import com.recon.api.service.ScimAuthenticationService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
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
public class ScimAuthenticationFilter extends OncePerRequestFilter {

    private static final String SCIM_MEDIA_TYPE = "application/scim+json";
    private static final String SCIM_PATH_PREFIX = "/api/scim/v2/";

    private final ScimAuthenticationService scimAuthenticationService;
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(SCIM_PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            filterChain.doFilter(request, response);
            return;
        }

        String tenantId = extractTenantId(request.getRequestURI());
        if (!StringUtils.hasText(tenantId)) {
            writeError(response, HttpStatus.BAD_REQUEST, "SCIM tenant id is missing from the request path");
            return;
        }

        String bearerToken = extractBearerToken(request);
        if (!StringUtils.hasText(bearerToken)) {
            response.setHeader("WWW-Authenticate", "Bearer realm=\"scim\"");
            writeError(response, HttpStatus.UNAUTHORIZED, "SCIM bearer token is required");
            return;
        }

        try {
            ReconUserPrincipal principal = scimAuthenticationService.authenticate(tenantId, bearerToken);
            var authorities = principal.getPermissions().stream()
                    .map(permission -> new SimpleGrantedAuthority("PERM_" + permission))
                    .collect(Collectors.toList());
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(principal, null, authorities));
            filterChain.doFilter(request, response);
        } catch (IllegalArgumentException ex) {
            log.warn("SCIM auth failed for tenant {}: {}", tenantId, ex.getMessage());
            response.setHeader("WWW-Authenticate", "Bearer realm=\"scim\"");
            writeError(response, HttpStatus.UNAUTHORIZED, ex.getMessage());
        }
    }

    private String extractTenantId(String requestUri) {
        if (!StringUtils.hasText(requestUri) || !requestUri.startsWith(SCIM_PATH_PREFIX)) {
            return null;
        }
        String remainder = requestUri.substring(SCIM_PATH_PREFIX.length());
        int slashIndex = remainder.indexOf('/');
        if (slashIndex < 0) {
            return remainder;
        }
        return remainder.substring(0, slashIndex);
    }

    private String extractBearerToken(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (StringUtils.hasText(authorization) && authorization.startsWith("Bearer ")) {
            return authorization.substring(7);
        }
        return null;
    }

    private void writeError(HttpServletResponse response,
                            HttpStatus status,
                            String detail) throws IOException {
        response.setStatus(status.value());
        response.setContentType(SCIM_MEDIA_TYPE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), ScimErrorResponse.builder()
                .detail(detail)
                .status(Integer.toString(status.value()))
                .build());
    }
}
