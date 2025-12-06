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

package org.idp.server.core.openid.authentication;

import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import org.idp.server.platform.log.LoggerWrapper;

public class AuthenticationInteractionRequest {

  private static final LoggerWrapper log =
      LoggerWrapper.getLogger(AuthenticationInteractionRequest.class);
  private static final int ABSOLUTE_MAX_LENGTH = 10240; // 10KB

  Map<String, Object> values;

  public static AuthenticationInteractionRequest empty() {
    return new AuthenticationInteractionRequest(Map.of());
  }

  public AuthenticationInteractionRequest(Map<String, Object> values) {
    this.values = Objects.requireNonNullElseGet(values, Map::of);
  }

  public Map<String, Object> toMap() {
    return values;
  }

  /**
   * Get value as String with basic security validation (Layer 1).
   *
   * <p><b>Issue #1008:</b> Prevents ClassCastException, SQL Injection, and log pollution.
   *
   * <p><b>Validation (mandatory for all tenants):</b>
   *
   * <ul>
   *   <li>Type check: Prevents ClassCastException
   *   <li>NULL character (\0): Prevents SQL Injection (PostgreSQL UTF-8 violation)
   *   <li>Control characters: Prevents log pollution and security event corruption
   *   <li>Absolute max length (10KB): Prevents DoS attacks
   * </ul>
   *
   * <p>Returns defaultValue if validation fails (fail-safe behavior).
   *
   * @param key field name
   * @param defaultValue default value if key is missing or validation fails
   * @return validated string value or defaultValue
   */
  public String optValueAsString(String key, String defaultValue) {
    if (!containsKey(key)) {
      return defaultValue;
    }

    Object value = values.get(key);

    // Null check
    if (value == null) {
      log.warn("Field '{}' is null. Returning default value.", key);
      return defaultValue;
    }

    // Type check (prevent ClassCastException)
    if (!(value instanceof String stringValue)) {
      log.warn(
          "Field '{}' type mismatch: expected String, got {}. Returning default value.",
          key,
          value.getClass().getSimpleName());
      return defaultValue;
    }

    // NULL character check (prevent SQL Injection - Issue #1008)
    // Security event: Potential attack pattern detected
    if (stringValue.contains("\0")) {
      log.error(
          "[SECURITY] Field '{}' contains NULL character (0x00). Potential SQL Injection attempt detected. Returning default value.",
          key);
      return defaultValue;
    }

    // Control character check (prevent log pollution)
    // Allow: Tab (0x09), LF (0x0A), CR (0x0D)
    // Security event: Potential attack pattern detected
    if (stringValue.chars().anyMatch(c -> c < 0x20 && c != 0x09 && c != 0x0A && c != 0x0D)) {
      log.error(
          "[SECURITY] Field '{}' contains control characters. Potential log injection attack detected. Returning default value.",
          key);
      return defaultValue;
    }

    // Absolute max length check (prevent DoS attacks)
    // Security event: Potential attack pattern detected
    if (stringValue.length() > ABSOLUTE_MAX_LENGTH) {
      log.error(
          "[SECURITY] Field '{}' exceeds absolute maximum length ({}). length={}. Potential DoS attack detected. Returning default value.",
          key,
          ABSOLUTE_MAX_LENGTH,
          stringValue.length());
      return defaultValue;
    }

    return stringValue;
  }

  /**
   * Get value as String with basic security validation (Layer 1).
   *
   * <p>Same validation as {@link #optValueAsString(String, String)} but throws exception if key is
   * missing.
   *
   * @param key field name
   * @return validated string value
   * @throws IllegalArgumentException if key is missing or validation fails
   */
  public String getValueAsString(String key) {
    if (!containsKey(key)) {
      throw new IllegalArgumentException("Field '" + key + "' is required");
    }

    String value = optValueAsString(key, null);

    if (value == null) {
      throw new IllegalArgumentException(
          "Field '" + key + "' validation failed (check logs for details)");
    }

    return value;
  }

  public boolean getValueAsBoolean(String key) {
    return (boolean) values.get(key);
  }

  public boolean containsKey(String key) {
    return values.containsKey(key);
  }

  public void forEach(BiConsumer<String, Object> action) {
    values.forEach(action);
  }

  public boolean exists() {
    return values != null && !values.isEmpty();
  }

  public Object getValue(String key) {
    return values.get(key);
  }
}
