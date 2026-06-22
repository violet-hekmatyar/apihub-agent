package com.apihub.mockprovider.common;

import com.apihub.mockprovider.scenario.ScenarioRunNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class MockProviderExceptionHandler {

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            IllegalArgumentException.class
    })
    public ResponseEntity<MockResponse<Map<String, Object>>> badRequest(Exception exception, HttpServletRequest request) {
        String message = exception instanceof IllegalArgumentException ? exception.getMessage() : "invalid request";
        return ResponseSupport.error(HttpStatus.BAD_REQUEST, message, traceId(request));
    }

    @ExceptionHandler(ScenarioRunNotFoundException.class)
    public ResponseEntity<MockResponse<Map<String, Object>>> notFound(ScenarioRunNotFoundException exception, HttpServletRequest request) {
        return ResponseSupport.error(HttpStatus.NOT_FOUND, exception.getMessage(), traceId(request));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<MockResponse<Map<String, Object>>> internalError(Exception exception, HttpServletRequest request) {
        return ResponseSupport.error(HttpStatus.INTERNAL_SERVER_ERROR, "mock provider internal error", traceId(request));
    }

    private static String traceId(HttpServletRequest request) {
        return ResponseSupport.safeTraceId(request.getHeader("X-Trace-Id"));
    }
}
