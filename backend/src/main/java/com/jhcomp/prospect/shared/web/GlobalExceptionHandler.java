package com.jhcomp.prospect.shared.web;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.jhcomp.prospect.shared.error.ApplicationException;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Tratamento global de erros (RNF-003, doc 05 §1).
 *
 * <p>Estende {@link ResponseEntityExceptionHandler} para que as excecoes do
 * proprio Spring MVC — rota inexistente, metodo nao suportado, corpo
 * malformado, falha de validacao — saiam no mesmo formato das excecoes da
 * aplicacao. Sem isso, a API responderia em dois formatos diferentes
 * dependendo de onde o erro nasceu.
 *
 * <p>Nenhuma resposta carrega stack trace ou mensagem interna. Falhas
 * inesperadas sao registradas no log com um identificador que tambem vai na
 * resposta, para permitir investigar sem expor o detalhe ao cliente.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Excecoes da aplicacao: tipo e status vem da propria excecao. */
    @ExceptionHandler(ApplicationException.class)
    public ResponseEntity<ApiErrorResponse> handleApplicationException(
            ApplicationException exception, HttpServletRequest request) {

        ApiErrorResponse body = ApiErrorResponse.of(
                exception.type(), exception.status(), exception.getMessage(), request.getRequestURI());

        return ResponseEntity.status(exception.status()).body(body);
    }

    /** Autorizacao negada pela seguranca de metodo. */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(
            AccessDeniedException exception, HttpServletRequest request) {

        // Nao registra a mensagem da excecao: ela pode citar a expressao de
        // autorizacao, que e detalhe interno.
        log.info("Acesso negado em {}", request.getRequestURI());

        ApiErrorResponse body = ApiErrorResponse.of(
                ApiErrorType.FORBIDDEN,
                HttpStatus.FORBIDDEN.value(),
                "You do not have permission to perform this operation",
                request.getRequestURI());

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    /** Validacao de DTO: um item por campo invalido, como no exemplo do doc 05 §1. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        List<ApiErrorResponse.FieldErrorDetail> fieldErrors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new ApiErrorResponse.FieldErrorDetail(
                        error.getField(),
                        error.getDefaultMessage() == null ? "is invalid" : error.getDefaultMessage()))
                .toList();

        ApiErrorResponse body = ApiErrorResponse.withFieldErrors(
                ApiErrorType.VALIDATION_ERROR,
                HttpStatus.BAD_REQUEST.value(),
                "One or more fields are invalid",
                requestUri(request),
                fieldErrors);

        return ResponseEntity.badRequest().body(body);
    }

    /**
     * Converte toda excecao tratada pelo Spring MVC para o formato da API.
     *
     * <p>O {@code body} original e descartado de proposito: o
     * {@code ProblemDetail} padrao traz textos como "No static resource ..."
     * que expoem detalhe de implementacao.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception exception,
            Object body,
            HttpHeaders headers,
            HttpStatusCode statusCode,
            WebRequest request) {

        HttpStatus status = HttpStatus.valueOf(statusCode.value());
        ApiErrorType type = toErrorType(status);

        ApiErrorResponse response = ApiErrorResponse.of(
                type, status.value(), type.title(), requestUri(request));

        return ResponseEntity.status(status).headers(headers).body(response);
    }

    /** Rede de seguranca: nada deve escapar sem o formato padrao. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception exception, HttpServletRequest request) {
        String incidentId = UUID.randomUUID().toString();

        // O detalhe fica no log do servidor, nao na resposta.
        log.error("Falha inesperada [incidente={}] em {}", incidentId, request.getRequestURI(), exception);

        ApiErrorResponse body = ApiErrorResponse.of(
                ApiErrorType.UNEXPECTED_ERROR,
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Unexpected error. Reference: " + incidentId,
                request.getRequestURI());

        return ResponseEntity.internalServerError().body(body);
    }

    private static ApiErrorType toErrorType(HttpStatus status) {
        return switch (status) {
            case NOT_FOUND -> ApiErrorType.RESOURCE_NOT_FOUND;
            case METHOD_NOT_ALLOWED -> ApiErrorType.METHOD_NOT_ALLOWED;
            case BAD_REQUEST -> ApiErrorType.MALFORMED_REQUEST;
            case FORBIDDEN -> ApiErrorType.FORBIDDEN;
            case UNAUTHORIZED -> ApiErrorType.AUTHENTICATION_REQUIRED;
            case CONFLICT -> ApiErrorType.CONFLICT;
            case TOO_MANY_REQUESTS -> ApiErrorType.TOO_MANY_REQUESTS;
            // CONTENT_TOO_LARGE e o nome atual do 413; PAYLOAD_TOO_LARGE foi
            // depreciado no Spring 7 para acompanhar a RFC 9110.
            case CONTENT_TOO_LARGE -> ApiErrorType.PAYLOAD_TOO_LARGE;
            default -> ApiErrorType.UNEXPECTED_ERROR;
        };
    }

    private static String requestUri(WebRequest request) {
        if (request instanceof ServletWebRequest servletRequest) {
            return servletRequest.getRequest().getRequestURI();
        }
        return request.getDescription(false).replaceFirst("^uri=", "");
    }
}
