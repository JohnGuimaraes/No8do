package com.no8do.api.auth;

import com.no8do.api.user.User;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class PersonalApiTokenAuthenticationFilter extends OncePerRequestFilter {
    private final PersonalApiTokenService tokenService;
    public PersonalApiTokenAuthenticationFilter(PersonalApiTokenService tokenService) { this.tokenService = tokenService; }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith("Bearer ")) { chain.doFilter(request, response); return; }
        User user = tokenService.authenticate(header.substring("Bearer ".length()));
        if (user == null) { response.setStatus(HttpStatus.UNAUTHORIZED.value()); return; }
        No8doUserDetails principal = new No8doUserDetails(user);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
        request.setAttribute(PersonalApiTokenService.CSRF_BYPASS_ATTRIBUTE, Boolean.TRUE);
        chain.doFilter(request, response);
    }
}
