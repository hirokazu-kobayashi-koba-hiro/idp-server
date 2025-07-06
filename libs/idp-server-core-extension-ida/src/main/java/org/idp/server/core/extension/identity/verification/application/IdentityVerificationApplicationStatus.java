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

package org.idp.server.core.extension.identity.verification.application;

import java.util.Map;
import java.util.Objects;
import org.idp.server.core.extension.identity.verification.IdentityVerificationApplicationRequest;
import org.idp.server.core.extension.identity.verification.configuration.IdentityVerificationProcessConfiguration;

public enum IdentityVerificationApplicationStatus {
  REQUESTED("requested"),
  APPLYING("applying"),
  EXAMINATION_PROCESSING("examination_processing"),
  APPROVED("approved"),
  REJECTED("rejected"),
  EXPIRED("expired"),
  CANCELLED("cancelled"),
  UNDEFINED(""),
  UNKNOWN("unknown");

  String value;

  IdentityVerificationApplicationStatus(String value) {
    this.value = value;
  }

  public static IdentityVerificationApplicationStatus of(String value) {
    for (IdentityVerificationApplicationStatus status :
        IdentityVerificationApplicationStatus.values()) {
      if (status.value.equals(value)) {
        return status;
      }
    }
    return UNKNOWN;
  }

  // TODO to be more flexible
  public static boolean isRejected(
      IdentityVerificationApplicationRequest request,
      IdentityVerificationProcessConfiguration processConfig) {

    Map<String, Object> rejectedConditionSchema = processConfig.rejectedConditionSchema();
    String type = (String) rejectedConditionSchema.getOrDefault("type", "");
    if (Objects.equals(type, "string")) {
      String field = (String) rejectedConditionSchema.getOrDefault("field", "");
      String expectedValue = (String) rejectedConditionSchema.getOrDefault("expected_value", "");

      return request.containsKey(field) && request.getValueAsString(field).equals(expectedValue);
    }

    if (Objects.equals(type, "boolean")) {
      String field = (String) rejectedConditionSchema.getOrDefault("field", "");
      Boolean expectedValue =
          (Boolean) rejectedConditionSchema.getOrDefault("expected_value", false);
      return request.containsKey(field) && request.getValueAsBoolean(field) == expectedValue;
    }

    return false;
  }

  public String value() {
    return value;
  }

  public boolean isRunning() {
    return this == REQUESTED || this == APPLYING || this == EXAMINATION_PROCESSING;
  }
}
