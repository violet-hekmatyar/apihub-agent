package com.apihub.agent.config;

import com.apihub.agent.common.TraceContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
public class TraceIdFilter extends OncePerRequestFilter {

    private static final String TRACE_HEADER = "X-Trace-Id";
    private static final String TRACEPARENT_HEADER = "traceparent";
    private static final Pattern TRACEPARENT_PATTERN = Pattern.compile("^[\\da-fA-F]{2}-([\\da-fA-F]{32})-[\\da-fA-F]{16}-[\\da-fA-F]{2}$");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String traceId = resolveTraceId(request);
        TraceContext.setTraceId(traceId);
        response.setHeader(TRACE_HEADER, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            TraceContext.clear();
        }
    }

    private String resolveTraceId(HttpServletRequest request) {
        String traceparent = request.getHeader(TRACEPARENT_HEADER);
        if (StringUtils.hasText(traceparent)) {
            var matcher = TRACEPARENT_PATTERN.matcher(traceparent.trim());
            if (matcher.matches()) {
                return matcher.group(1).toLowerCase();
            }
        }
        String traceId = request.getHeader(TRACE_HEADER);
        if (StringUtils.hasText(traceId)) {
            return traceId.trim();
        }
        return UUID.randomUUID().toString().replace("-", "");
    }
}
