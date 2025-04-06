package org.idp.server.core.authentication.email;

import java.util.Map;

public class EmailVerificationResult {

  boolean result;
  Map<String, Object> response;

  public static EmailVerificationResult success(Map<String, Object> response) {
    return new EmailVerificationResult(true, response);
  }

  public static EmailVerificationResult failure(Map<String, Object> response) {
    return new EmailVerificationResult(false, response);
  }

  public EmailVerificationResult(boolean result, Map<String, Object> response) {
    this.result = result;
    this.response = response;
  }

  public boolean isSuccess() {
    return result;
  }

  public boolean isFailure() {
    return !result;
  }

  public Map<String, Object> response() {
    return response;
  }
}
