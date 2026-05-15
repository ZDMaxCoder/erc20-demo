package com.erc20.platform.admin.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

@Slf4j
@Component
@Order(1)
public class AdminAuthFilter implements Filter {

    public static final String HEADER_OPERATOR = "X-Operator";
    public static final String ATTR_OPERATOR = "operator";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String operator = httpRequest.getHeader(HEADER_OPERATOR);
        if (operator != null && !operator.isEmpty()) {
            httpRequest.setAttribute(ATTR_OPERATOR, operator);
        } else {
            httpRequest.setAttribute(ATTR_OPERATOR, "system");
        }
        chain.doFilter(request, response);
    }
}
