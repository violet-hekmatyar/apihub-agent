package com.apihub.agent.exception;

import com.apihub.agent.common.BaseResponse;
import com.apihub.agent.common.ErrorCode;
import com.apihub.agent.common.ResultUtils;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public BaseResponse<Object> handleBusinessException(BusinessException e) {
        if (e.getErrorCode() == ErrorCode.INTERNAL_ERROR) {
            log.error("business exception", e);
        } else {
            log.warn("business exception: {}", e.getMessage());
        }
        return ResultUtils.error(e.getErrorCode(), e.getMessage());
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            ConstraintViolationException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class
    })
    public BaseResponse<Object> handleInvalidArgument(Exception e) {
        log.warn("invalid argument: {}", e.getMessage());
        return ResultUtils.error(ErrorCode.INVALID_ARGUMENT);
    }

    @ExceptionHandler(Exception.class)
    public BaseResponse<Object> handleException(Exception e) {
        log.error("unexpected server error", e);
        return ResultUtils.error(ErrorCode.INTERNAL_ERROR);
    }
}
