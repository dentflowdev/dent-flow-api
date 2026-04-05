package com.dentalManagement.dentalFlowBackend.config;


import com.dentalManagement.dentalFlowBackend.exception.FilterErrorResponseUtil;
import com.dentalManagement.dentalFlowBackend.service.JwtService;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import lombok.RequiredArgsConstructor;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {

        final String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            chain.doFilter(request, response);
            return;    // ← return early, no need for try/finally pattern here
        }

        try {
            String jwt = authHeader.substring(7);
            String username = jwtService.extractUsername(jwt);

            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                if (jwtService.isTokenValid(jwt, userDetails)) {
                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails, null, userDetails.getAuthorities());
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
        }  catch (ExpiredJwtException ex) {
            FilterErrorResponseUtil.sendErrorResponse(
                    response, 401, "Unauthorized", "Token expired");
            return;
        } catch (JwtException ex) {
            FilterErrorResponseUtil.sendErrorResponse(
                    response, 401, "Unauthorized", "Invalid token");
            return;
        } catch (Exception ex) {
            FilterErrorResponseUtil.sendErrorResponse(
                    response, 401, "Unauthorized", "Authentication failed");
            return;
        }
        // Note: SecurityContext is managed per-thread by Spring's SecurityContextPersistenceFilter
        // in STATELESS mode — no need to manually clearContext()
        chain.doFilter(request, response);
    }
}
