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

package org.idp.server.authentication.interactors.attribute_verification;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The tenant's attribute verification settings, read from {@code execution.details} of the {@code
 * attribute-verification} interaction.
 *
 * <pre>{@code
 * {
 *   "fields": [
 *     { "input": "birthdate", "user_attribute": "birthdate", "normalize": "date" },
 *     { "input": "phone_last4", "user_attribute": "phone_number", "normalize": "digits",
 *       "suffix_length": 4 }
 *   ],
 *   "max_attempts": 5,
 *   "lockout_seconds": 900
 * }
 * }</pre>
 *
 * <p>A setting that cannot be read correctly makes the whole configuration invalid rather than
 * being skipped: dropping one field would quietly turn a two-item check into a one-item check.
 */
public class AttributeVerificationConfig {

  static final int DEFAULT_MAX_ATTEMPTS = 5;
  static final int DEFAULT_LOCKOUT_SECONDS = 900;

  List<AttributeVerificationField> fields;
  int maxAttempts;
  int lockoutSeconds;
  String invalidReason;

  private AttributeVerificationConfig(
      List<AttributeVerificationField> fields,
      int maxAttempts,
      int lockoutSeconds,
      String invalidReason) {
    this.fields = fields;
    this.maxAttempts = maxAttempts;
    this.lockoutSeconds = lockoutSeconds;
    this.invalidReason = invalidReason;
  }

  public static AttributeVerificationConfig from(Map<String, Object> details) {
    if (details == null || !(details.get("fields") instanceof List<?> rawFields)) {
      return invalid("fields is not configured.");
    }
    if (rawFields.isEmpty()) {
      return invalid("fields is empty.");
    }

    List<AttributeVerificationField> fields = new ArrayList<>();
    for (Object rawField : rawFields) {
      if (!(rawField instanceof Map<?, ?> field)) {
        return invalid("each field must be an object.");
      }
      String input = field.get("input") instanceof String value ? value : null;
      if (input == null || input.isEmpty()) {
        return invalid("field input is required.");
      }
      String userAttribute = field.get("user_attribute") instanceof String value ? value : null;
      if (!VerifiableUserAttributes.isVerifiable(userAttribute)) {
        return invalid("field user_attribute is not a verifiable attribute: " + userAttribute);
      }
      Object rawNormalize = field.get("normalize");
      AttributeNormalization normalization =
          rawNormalize == null
              ? AttributeNormalization.EXACT
              : AttributeNormalization.of(String.valueOf(rawNormalize));
      if (normalization == null) {
        return invalid("field normalize is not supported: " + rawNormalize);
      }
      int suffixLength = intOf(field.get("suffix_length"), 0);
      if (suffixLength < 0) {
        return invalid("field suffix_length must be a non-negative integer.");
      }
      fields.add(new AttributeVerificationField(input, userAttribute, normalization, suffixLength));
    }

    int maxAttempts = intOf(details.get("max_attempts"), DEFAULT_MAX_ATTEMPTS);
    int lockoutSeconds = intOf(details.get("lockout_seconds"), DEFAULT_LOCKOUT_SECONDS);
    if (maxAttempts <= 0 || lockoutSeconds <= 0) {
      return invalid("max_attempts and lockout_seconds must be positive integers.");
    }
    return new AttributeVerificationConfig(fields, maxAttempts, lockoutSeconds, null);
  }

  private static AttributeVerificationConfig invalid(String reason) {
    return new AttributeVerificationConfig(List.of(), 0, 0, reason);
  }

  private static int intOf(Object value, int defaultValue) {
    if (value == null) {
      return defaultValue;
    }
    if (value instanceof Number number) {
      return number.intValue();
    }
    return -1;
  }

  public boolean isValid() {
    return invalidReason == null;
  }

  public String invalidReason() {
    return invalidReason;
  }

  public List<AttributeVerificationField> fields() {
    return fields;
  }

  /** Failed attempts allowed per user before further attempts are refused. */
  public int maxAttempts() {
    return maxAttempts;
  }

  /** How long the refusal lasts, counted from the first failed attempt. */
  public int lockoutSeconds() {
    return lockoutSeconds;
  }
}
