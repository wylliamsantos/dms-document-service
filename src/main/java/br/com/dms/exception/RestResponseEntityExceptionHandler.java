package br.com.dms.exception;

import org.apache.tomcat.util.http.fileupload.impl.SizeException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.stream.Collectors;

import static org.apache.commons.lang3.StringUtils.join;

@ControllerAdvice
public class RestResponseEntityExceptionHandler extends ResponseEntityExceptionHandler {

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {

        String erros = join(ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(". ")));
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setContentType(MediaType.APPLICATION_JSON);
        DefaultError defaultError = new DefaultError(erros, TypeException.VALID, null);
        return handleExceptionInternal(ex, defaultError, httpHeaders, HttpStatus.BAD_REQUEST, request);
    }

    // 413 MultipartException - file size too big
    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<Object> handleSizeExceededException(final WebRequest request, final MultipartException ex) {
        Throwable cause = ex.getCause();
        if (cause instanceof IllegalStateException) {
            Throwable cause2 = cause.getCause();
            if (cause2 instanceof SizeException) {
                // this is tomcat specific

                HttpHeaders httpHeaders = new HttpHeaders();
                httpHeaders.setContentType(MediaType.APPLICATION_JSON);
                return handleExceptionInternal(ex, "", httpHeaders, HttpStatus.PAYLOAD_TOO_LARGE, request);
            }

        }
        return handleExceptionInternal(ex, ex, new HttpHeaders(), HttpStatus.INTERNAL_SERVER_ERROR, request);
    }

    // 417 Generic error for business
    @ExceptionHandler(DmsBusinessException.class)
    public ResponseEntity<Object> handleDmsBusinessException(final WebRequest request, final DmsBusinessException ex) {
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setContentType(MediaType.APPLICATION_JSON);
        DefaultError defaultError = new DefaultError(ex.getMessage(), ex.getType(), ex.getTransactionId());
        return handleExceptionInternal(ex, defaultError, httpHeaders, HttpStatus.EXPECTATION_FAILED, request);

    }


    // 404 Resource not found
    @ExceptionHandler(DmsDocumentNotFoundException.class)
    public ResponseEntity<Object> handleDmsDocumentNotFoundException(final WebRequest request, final DmsDocumentNotFoundException ex) {
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setContentType(MediaType.APPLICATION_JSON);
        DefaultError defaultError = new DefaultError(ex.getMessage(), ex.getType(), ex.getTransactionId());
        return handleExceptionInternal(ex, defaultError, httpHeaders, HttpStatus.NOT_FOUND, request);

    }

    // 500 Erro geral da API
    @ExceptionHandler(DmsException.class)
    public ResponseEntity<Object> handleDmsException(final WebRequest request, final DmsException ex) {
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setContentType(MediaType.APPLICATION_JSON);
        DefaultError defaultError = new DefaultError(ex.getMessage(), ex.getType(), ex.getTransactionId());
        return handleExceptionInternal(ex, defaultError, httpHeaders, HttpStatus.INTERNAL_SERVER_ERROR, request);

    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<Object> handleDuplicateKeyException(final WebRequest request, final DuplicateKeyException ex) {
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setContentType(MediaType.APPLICATION_JSON);
        DefaultError defaultError = new DefaultError(ex.getMessage(), TypeException.VALID, null);
        return handleExceptionInternal(ex, defaultError, httpHeaders, HttpStatus.CONFLICT, request);

    }

}
