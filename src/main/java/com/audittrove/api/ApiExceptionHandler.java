package com.audittrove.api;

import com.audittrove.audit.InvalidDocumentException;
import com.audittrove.llm.LlmUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler({InvalidDocumentException.class, MethodArgumentNotValidException.class,
            MaxUploadSizeExceededException.class})
    ProblemDetail badRequest(Exception exception) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
        detail.setTitle("Geçersiz doküman");
        return detail;
    }

    @ExceptionHandler(LlmUnavailableException.class)
    ProblemDetail serviceUnavailable(LlmUnavailableException exception) {
        log.error("LLM request failed", exception);
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE, "Analiz servisi şu anda kullanılamıyor");
        detail.setTitle("Analiz tamamlanamadı");
        return detail;
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception exception) {
        log.error("Unexpected request failure", exception);
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "Beklenmeyen bir hata oluştu");
        detail.setTitle("İşlem tamamlanamadı");
        return detail;
    }
}
