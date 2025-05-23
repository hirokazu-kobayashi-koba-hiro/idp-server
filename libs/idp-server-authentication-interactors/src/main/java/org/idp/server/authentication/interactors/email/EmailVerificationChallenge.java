/*
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.idp.server.authentication.interactors.email;

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
