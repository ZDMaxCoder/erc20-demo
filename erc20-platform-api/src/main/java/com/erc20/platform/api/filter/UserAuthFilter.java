package com.erc20.platform.api.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Slf4j
@Component
@Order(1)
public class UserAuthFilter implements Filter {

    public static final String HEADER_USER_ID = "X-User-Id";
    public static final String ATTR_USER_ID = "userId";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String path = httpRequest.getRequestURI();

        if (!path.startsWith("/api/v1/")) {
            chain.doFilter(request, response);
            return;
        }

        String userId = httpRequest.getHeader(HEADER_USER_ID);
        if (userId == null || userId.isEmpty()) {
            HttpServletResponse httpResponse = (HttpServletResponse) response;
            httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            httpResponse.setContentType("application/json;charset=UTF-8");
            httpResponse.getWriter().write("{\"code\":401,\"message\":\"Unauthorized\"}");
            return;
        }

        httpRequest.setAttribute(ATTR_USER_ID, userId);
        chain.doFilter(request, response);
    }
}
