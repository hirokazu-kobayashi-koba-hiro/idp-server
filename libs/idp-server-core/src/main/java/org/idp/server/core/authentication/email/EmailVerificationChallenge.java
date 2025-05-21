package org.idp.server.core.authentication.email;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.idp.server.basic.json.JsonReadable;
import org.idp.server.platform.date.SystemDateTime;

public class EmailVerificationChallenge implements Serializable, JsonReadable {

  String verificationCode;
  int retryCountLimitation;
  int tryCount;
  int expiresSeconds;
  LocalDateTime createdAt;

  public EmailVerificationChallenge() {}

  public static EmailVerificationChallenge create(
      OneTimePassword oneTimePassword, int retryCountLimitation, int expiresSeconds) {
    return new EmailVerificationChallenge(
        oneTimePassword.value(), retryCountLimitation, 0, expiresSeconds, SystemDateTime.now());
  }

  public EmailVerificationChallenge countUp() {
    int newTryCount = tryCount + 1;
    return new EmailVerificationChallenge(
        verificationCode, retryCountLimitation, newTryCount, expiresSeconds, createdAt);
  }

  public EmailVerificationChallenge(
      String verificationCode,
      int retryCountLimitation,
      int tryCount,
      int expiresSeconds,
      LocalDateTime createdAt) {
    this.verificationCode = verificationCode;
    this.retryCountLimitation = retryCountLimitation;
    this.tryCount = tryCount;
    this.expiresSeconds = expiresSeconds;
    this.createdAt = createdAt;
  }

  public EmailVerificationResult verify(String input) {

    if (isExpired()) {
      Map<String, Object> response = new HashMap<>();
      response.put("error", "invalid_request");
      response.put("error_description", "email challenge is expired");

      return EmailVerificationResult.failure(response);
    }

    if (tryCount >= retryCountLimitation) {
      Map<String, Object> response = new HashMap<>();
      response.put("error", "invalid_request");
      response.put(
          "error_description",
          "email challenge is reached limited to " + retryCountLimitation + " attempts");

      return EmailVerificationResult.failure(response);
    }

    if (!Objects.equals(verificationCode, input)) {
      Map<String, Object> response = new HashMap<>();
      response.put("error", "invalid_request");
      response.put("error_description", "Invalid verification code");

      return EmailVerificationResult.failure(response);
    }

    return EmailVerificationResult.success(Map.of("status", "success"));
  }

  private boolean isExpired() {
    return SystemDateTime.now().isAfter(createdAt.plusSeconds(expiresSeconds));
  }
}
