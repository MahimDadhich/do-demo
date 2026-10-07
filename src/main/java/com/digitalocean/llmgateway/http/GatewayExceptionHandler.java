package com.digitalocean.llmgateway.http;

import com.digitalocean.llmgateway.router.GatewayError;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;

@RestControllerAdvice
public class GatewayExceptionHandler {

  @ExceptionHandler(GatewayError.class)
  public Mono<ResponseEntity<Map<String, Object>>> handleGatewayError(GatewayError error) {
    int status = error.getHttpStatus() > 0 ? error.getHttpStatus() : 500;
    return Mono.just(
        ResponseEntity.status(status)
            .contentType(MediaType.APPLICATION_JSON)
            .body(errorBody(error.getErrorClass().name().toLowerCase(), error.getMessage())));
  }

  @ExceptionHandler({WebExchangeBindException.class, ServerWebInputException.class})
  public Mono<ResponseEntity<Map<String, Object>>> handleValidation(Exception error) {
    return Mono.just(
        ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .contentType(MediaType.APPLICATION_JSON)
            .body(errorBody("invalid_request", error.getMessage())));
  }

  @ExceptionHandler(Throwable.class)
  public Mono<ResponseEntity<Map<String, Object>>> handleOther(Throwable error) {
    if (error instanceof GatewayError gatewayError) {
      return handleGatewayError(gatewayError);
    }
    return Mono.just(
        ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .contentType(MediaType.APPLICATION_JSON)
            .body(errorBody("internal_error", error.getMessage())));
  }

  public static Map<String, Object> errorBody(String code, String message) {
    Map<String, Object> error = new LinkedHashMap<>();
    error.put("message", message == null ? code : message);
    error.put("type", "gateway_error");
    error.put("code", code);
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("error", error);
    return body;
  }
}
