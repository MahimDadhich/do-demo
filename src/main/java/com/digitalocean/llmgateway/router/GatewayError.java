package com.digitalocean.llmgateway.router;

public class GatewayError extends RuntimeException {

  private final ErrorClass errorClass;
  private final boolean failoverEligible;
  private final int httpStatus;

  public GatewayError(
      ErrorClass errorClass, boolean failoverEligible, int httpStatus, String message) {
    this(errorClass, failoverEligible, httpStatus, message, null);
  }

  public GatewayError(
      ErrorClass errorClass,
      boolean failoverEligible,
      int httpStatus,
      String message,
      Throwable cause) {
    super(message, cause);
    this.errorClass = errorClass;
    this.failoverEligible = failoverEligible;
    this.httpStatus = httpStatus;
  }

  public ErrorClass getErrorClass() {
    return errorClass;
  }

  public boolean isFailoverEligible() {
    return failoverEligible;
  }

  public int getHttpStatus() {
    return httpStatus;
  }

  public static GatewayError client(int status, String message) {
    return new GatewayError(ErrorClass.CLIENT_ERROR, false, status, message);
  }

  public static GatewayError modelNotFound(String model) {
    return new GatewayError(
        ErrorClass.CLIENT_ERROR, false, 404, "model_not_found: unknown logical model '" + model + "'");
  }

  public static GatewayError rateLimited(String message) {
    return new GatewayError(ErrorClass.RATE_LIMITED, true, 429, message);
  }

  public static GatewayError unavailable(int status, String message) {
    return new GatewayError(ErrorClass.UPSTREAM_UNAVAILABLE, true, status, message);
  }

  public static GatewayError timeout(String message) {
    return new GatewayError(ErrorClass.TIMEOUT, true, 504, message);
  }

  public static GatewayError fatal(String message) {
    return new GatewayError(ErrorClass.UPSTREAM_FATAL, false, 502, message);
  }

  public static GatewayError fromUpstreamStatus(int status, String body) {
    if (status == 429) {
      return rateLimited("upstream rate limited: " + truncate(body));
    }
    if (status >= 500) {
      return unavailable(status, "upstream error " + status + ": " + truncate(body));
    }
    if (status >= 400) {
      return client(status, "upstream client error " + status + ": " + truncate(body));
    }
    return unavailable(status, "unexpected upstream status " + status + ": " + truncate(body));
  }

  private static String truncate(String body) {
    if (body == null) {
      return "";
    }
    return body.length() > 300 ? body.substring(0, 300) + "…" : body;
  }
}
