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

package org.idp.server.authentication.interactors.sms.internal;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.idp.server.authentication.interactors.email.OneTimePassword;
import org.idp.server.platform.date.SystemDateTime;
import org.idp.server.platform.json.JsonReadable;

public class SmsVerificationChallenge implements Serializable, JsonReadable {

  String verificationCode;
  int retryCountLimitation;
  int tryCount;
  int expiresSeconds;
  LocalDateTime createdAt;

  public SmsVerificationChallenge() {}

  public static SmsVerificationChallenge create(
      OneTimePassword oneTimePassword, int retryCountLimitation, int expiresSeconds) {
    return new SmsVerificationChallenge(
        oneTimePassword.value(), retryCountLimitation, 0, expiresSeconds, SystemDateTime.now());
  }

  public SmsVerificationChallenge countUp() {
    int newTryCount = tryCount + 1;
    return new SmsVerificationChallenge(
        verificationCode, retryCountLimitation, newTryCount, expiresSeconds, createdAt);
  }

  public SmsVerificationChallenge(
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

  public SmsVerificationResult verify(String input) {

    if (isExpired()) {
      Map<String, Object> response = new HashMap<>();
      response.put("error", "invalid_request");
      response.put("error_description", "email challenge is expired");

      return SmsVerificationResult.failure(response);
    }

    if (tryCount >= retryCountLimitation) {
      Map<String, Object> response = new HashMap<>();
      response.put("error", "invalid_request");
      response.put(
          "error_description",
          "email challenge is reached limited to " + retryCountLimitation + " attempts");

      return SmsVerificationResult.failure(response);
    }

    if (!Objects.equals(verificationCode, input)) {
      Map<String, Object> response = new HashMap<>();
      response.put("error", "invalid_request");
      response.put("error_description", "Invalid verification code");

      return SmsVerificationResult.failure(response);
    }

    return SmsVerificationResult.success(Map.of("status", "success"));
  }

  private boolean isExpired() {
    return SystemDateTime.now().isAfter(createdAt.plusSeconds(expiresSeconds));
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    map.put("verification_code", verificationCode);
    map.put("retry_count_limitation", retryCountLimitation);
    map.put("try_count", tryCount);
    map.put("expires_seconds", expiresSeconds);
    map.put("created_at", createdAt);
    return map;
  }
}
