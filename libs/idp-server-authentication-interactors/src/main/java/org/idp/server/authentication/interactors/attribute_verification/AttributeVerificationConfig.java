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
import java.util.regex.Pattern;
import org.idp.server.core.openid.authentication.policy.AuthenticationResultCondition;
import org.idp.server.core.openid.authentication.policy.AuthenticationResultConditionConfig;
import org.idp.server.platform.condition.ConditionOperation;
import org.idp.server.platform.json.JsonConverter;

/**
 * The settings of one named attribute verification interaction, read from its {@code
 * execution.details}.
 *
 * <p>Each interaction is exactly one kind of check:
 *
 * <pre>{@code
 * // what the account already is — nothing is entered
 * {
 *   "conditions": {
 *     "any_of": [[ { "path": "$.user.status", "type": "string", "operation": "eq",
 *                    "value": "IDENTITY_VERIFIED" } ]]
 *   },
 *   "error": "identity_verification_required"
 * }
 *
 * // values the end-user enters
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
 * <p>One kind per interaction keeps what is recorded under its name unambiguous: an interaction's
 * failures are either failed guesses or accounts that do not qualify, never a mix, so a policy can
 * lock on the one without the other. A tenant wanting both places two interactions as two steps.
 *
 * <p>A setting that cannot be read correctly makes the whole configuration invalid rather than
 * being skipped: dropping one field would quietly turn a two-item check into a one-item check.
 */
public class AttributeVerificationConfig {

  static final int DEFAULT_MAX_ATTEMPTS = 5;
  static final int DEFAULT_LOCKOUT_SECONDS = 900;
  static final String DEFAULT_CONDITION_ERROR = "attribute_condition_not_satisfied";

  /** An error code, as the authorization view will branch on it. */
  private static final Pattern ERROR_CODE = Pattern.compile("[a-z0-9_]{1,64}");

  private static final JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();

  AuthenticationResultConditionConfig conditions;
  String conditionError;
  List<AttributeVerificationField> fields;
  int maxAttempts;
  int lockoutSeconds;
  String invalidReason;

  private AttributeVerificationConfig(
      AuthenticationResultConditionConfig conditions,
      String conditionError,
      List<AttributeVerificationField> fields,
      int maxAttempts,
      int lockoutSeconds,
      String invalidReason) {
    this.conditions = conditions;
    this.conditionError = conditionError;
    this.fields = fields;
    this.maxAttempts = maxAttempts;
    this.lockoutSeconds = lockoutSeconds;
    this.invalidReason = invalidReason;
  }

  public static AttributeVerificationConfig from(Map<String, Object> details) {
    if (details == null || (!details.containsKey("conditions") && !details.containsKey("fields"))) {
      return invalid("neither conditions nor fields is configured.");
    }
    if (details.containsKey("conditions") && details.containsKey("fields")) {
      return invalid(
          "conditions and fields cannot both be set in one interaction; use two interactions.");
    }
    if (details.containsKey("fields") && details.containsKey("error")) {
      return invalid("error applies to conditions only.");
    }

    AuthenticationResultConditionConfig conditions = new AuthenticationResultConditionConfig();
    if (details.containsKey("conditions")) {
      conditions = parseConditions(details.get("conditions"));
      if (conditions == null) {
        return invalid("conditions must be an any_of of non-empty groups of known operations.");
      }
    }
    Object rawError = details.getOrDefault("error", DEFAULT_CONDITION_ERROR);
    if (!(rawError instanceof String conditionError)
        || !ERROR_CODE.matcher(conditionError).matches()) {
      return invalid("error must be lowercase letters, digits and underscores: " + rawError);
    }

    List<AttributeVerificationField> fields = List.of();
    if (details.containsKey("fields")) {
      FieldsParse parsed = parseFields(details.get("fields"));
      if (parsed.invalidReason() != null) {
        return invalid(parsed.invalidReason());
      }
      fields = parsed.fields();
    }

    int maxAttempts = intOf(details.get("max_attempts"), DEFAULT_MAX_ATTEMPTS);
    int lockoutSeconds = intOf(details.get("lockout_seconds"), DEFAULT_LOCKOUT_SECONDS);
    if (maxAttempts <= 0 || lockoutSeconds <= 0) {
      return invalid("max_attempts and lockout_seconds must be positive integers.");
    }
    return new AttributeVerificationConfig(
        conditions, conditionError, fields, maxAttempts, lockoutSeconds, null);
  }

  /**
   * @return the conditions, or null when they cannot be read. An unknown operation is refused here
   *     rather than left to evaluate as false, so a typo is found when the step is first used.
   */
  private static AuthenticationResultConditionConfig parseConditions(Object raw) {
    if (!(raw instanceof Map<?, ?>)) {
      return null;
    }
    AuthenticationResultConditionConfig config;
    try {
      config = jsonConverter.read(raw, AuthenticationResultConditionConfig.class);
    } catch (RuntimeException e) {
      return null;
    }
    if (config == null || !config.exists()) {
      return null;
    }
    for (List<AuthenticationResultCondition> group : config.anyOf()) {
      if (group == null || group.isEmpty()) {
        return null;
      }
      for (AuthenticationResultCondition condition : group) {
        if (condition == null
            || condition.path() == null
            || condition.path().isEmpty()
            || ConditionOperation.from(condition.operation()) == ConditionOperation.UNKNOWN) {
          return null;
        }
      }
    }
    return config;
  }

  private static FieldsParse parseFields(Object raw) {
    if (!(raw instanceof List<?> rawFields)) {
      return FieldsParse.invalid("fields must be a list.");
    }
    if (rawFields.isEmpty()) {
      return FieldsParse.invalid("fields is empty.");
    }
    List<AttributeVerificationField> fields = new ArrayList<>();
    for (Object rawField : rawFields) {
      if (!(rawField instanceof Map<?, ?> field)) {
        return FieldsParse.invalid("each field must be an object.");
      }
      String input = field.get("input") instanceof String value ? value : null;
      if (input == null || input.isEmpty()) {
        return FieldsParse.invalid("field input is required.");
      }
      String userAttribute = field.get("user_attribute") instanceof String value ? value : null;
      if (!VerifiableUserAttributes.isVerifiable(userAttribute)) {
        return FieldsParse.invalid(
            "field user_attribute is not a verifiable attribute: " + userAttribute);
      }
      Object rawNormalize = field.get("normalize");
      AttributeNormalization normalization =
          rawNormalize == null
              ? AttributeNormalization.EXACT
              : AttributeNormalization.of(String.valueOf(rawNormalize));
      if (normalization == null) {
        return FieldsParse.invalid("field normalize is not supported: " + rawNormalize);
      }
      int suffixLength = intOf(field.get("suffix_length"), 0);
      if (suffixLength < 0) {
        return FieldsParse.invalid("field suffix_length must be a non-negative integer.");
      }
      fields.add(new AttributeVerificationField(input, userAttribute, normalization, suffixLength));
    }
    return new FieldsParse(fields, null);
  }

  private static AttributeVerificationConfig invalid(String reason) {
    return new AttributeVerificationConfig(
        new AuthenticationResultConditionConfig(), null, List.of(), 0, 0, reason);
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

  public boolean hasConditions() {
    return conditions.exists();
  }

  public AuthenticationResultConditionConfig conditions() {
    return conditions;
  }

  /** The error code returned when the conditions do not hold. */
  public String conditionError() {
    return conditionError;
  }

  public boolean hasFields() {
    return !fields.isEmpty();
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

  private record FieldsParse(List<AttributeVerificationField> fields, String invalidReason) {
    static FieldsParse invalid(String reason) {
      return new FieldsParse(List.of(), reason);
    }
  }
}
