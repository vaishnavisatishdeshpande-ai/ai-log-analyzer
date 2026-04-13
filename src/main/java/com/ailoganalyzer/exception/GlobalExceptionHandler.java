package com.ailoganalyzer.exception;

import com.ailoganalyzer.constant.ErrorConstants;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import jakarta.servlet.http.HttpServletRequest;
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(LogNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleLogNotFound(LogNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(ErrorConstants.LOG_NOT_FOUND_MESSAGE, ErrorConstants.LOG_NOT_FOUND_CODE));
    }

    @ExceptionHandler(AnalysisException.class)
    public ResponseEntity<ErrorResponse> handleAnalysisException(AnalysisException e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(ErrorConstants.ANALYSIS_FAILED_MESSAGE, ErrorConstants.ANALYSIS_ERROR_CODE));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception e, HttpServletRequest request) {

        String path = request.getRequestURI();

        if (path.contains("/h2-console")) {
            throw new RuntimeException(e);
        }

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(
                        ErrorConstants.INTERNAL_SERVER_ERROR_MESSAGE,
                        ErrorConstants.INTERNAL_ERROR_CODE
                ));
    }
}
