package com.digitalocean.llmgateway.router;

public enum ErrorClass {
  CLIENT_ERROR,
  RATE_LIMITED,
  UPSTREAM_UNAVAILABLE,
  TIMEOUT,
  UPSTREAM_FATAL
}
