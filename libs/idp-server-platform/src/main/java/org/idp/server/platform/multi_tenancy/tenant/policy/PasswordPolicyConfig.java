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

package org.idp.server.platform.multi_tenancy.tenant.policy;

import java.util.HashMap;
import java.util.Map;

/**
 * Password policy configuration for a tenant.
 *
 * <p>Defines password requirements based on OWASP and NIST guidelines.
 *
 * @see <a href="https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html">
 *     OWASP Password Storage Cheat Sheet</a>
 * @see <a href="https://pages.nist.gov/800-63-3/sp800-63b.html">NIST SP 800-63B</a>
 */
public class PasswordPolicyConfig {

  private static final int DEFAULT_MIN_LENGTH = 8;
  private static final int DEFAULT_MAX_LENGTH = 72; // BCrypt limitation

  private int minLength;
  private int maxLength;
  private boolean requireUppercase;
  private boolean requireLowercase;
  private boolean requireNumber;
  private boolean requireSpecialChar;
  private String customRegex;
  private String customRegexErrorMessage;
  private int maxHistory; // For future use (Issue #741 Phase 2)

  public PasswordPolicyConfig() {
    this.minLength = DEFAULT_MIN_LENGTH;
    this.maxLength = DEFAULT_MAX_LENGTH;
    this.requireUppercase = false;
    this.requireLowercase = false;
    this.requireNumber = false;
    this.requireSpecialChar = false;
    this.customRegex = null;
    this.customRegexErrorMessage = null;
    this.maxHistory = 0;
  }

  public PasswordPolicyConfig(
      int minLength,
      boolean requireUppercase,
      boolean requireLowercase,
      boolean requireNumber,
      boolean requireSpecialChar,
      int maxHistory) {
    this(
        minLength,
        DEFAULT_MAX_LENGTH,
        requireUppercase,
        requireLowercase,
        requireNumber,
        requireSpecialChar,
        null,
        null,
        maxHistory);
  }

  public PasswordPolicyConfig(
      int minLength,
      boolean requireUppercase,
      boolean requireLowercase,
      boolean requireNumber,
      boolean requireSpecialChar,
      String customRegex,
      String customRegexErrorMessage,
      int maxHistory) {
    this(
        minLength,
        DEFAULT_MAX_LENGTH,
        requireUppercase,
        requireLowercase,
        requireNumber,
        requireSpecialChar,
        customRegex,
        customRegexErrorMessage,
        maxHistory);
  }

  public PasswordPolicyConfig(
      int minLength,
      int maxLength,
      boolean requireUppercase,
      boolean requireLowercase,
      boolean requireNumber,
      boolean requireSpecialChar,
      String customRegex,
      String customRegexErrorMessage,
      int maxHistory) {
    this.minLength = minLength;
    this.maxLength = maxLength;
    this.requireUppercase = requireUppercase;
    this.requireLowercase = requireLowercase;
    this.requireNumber = requireNumber;
    this.requireSpecialChar = requireSpecialChar;
    this.customRegex = customRegex;
    this.customRegexErrorMessage = customRegexErrorMessage;
    this.maxHistory = maxHistory;
  }

  /**
   * Default password policy following OWASP/NIST recommendations.
   *
   * <p>Minimum 8 characters, no complexity requirements (NIST recommends against strict
   * complexity).
   */
  public static PasswordPolicyConfig defaultPolicy() {
    return new PasswordPolicyConfig();
  }

  /**
   * Constructs password policy from map.
   *
   * @param map configuration map
   * @return password policy config
   */
  public static PasswordPolicyConfig fromMap(Map<String, Object> map) {
    if (map == null || map.isEmpty()) {
      return defaultPolicy();
    }

    int minLength = getIntValue(map, "min_length", DEFAULT_MIN_LENGTH);
    int maxLength = getIntValue(map, "max_length", DEFAULT_MAX_LENGTH);
    boolean requireUppercase = getBooleanValue(map, "require_uppercase", false);
    boolean requireLowercase = getBooleanValue(map, "require_lowercase", false);
    boolean requireNumber = getBooleanValue(map, "require_number", false);
    boolean requireSpecialChar = getBooleanValue(map, "require_special_char", false);
    String customRegex = getStringValue(map, "custom_regex", null);
    String customRegexErrorMessage = getStringValue(map, "custom_regex_error_message", null);
    int maxHistory = getIntValue(map, "max_history", 0);

    return new PasswordPolicyConfig(
        minLength,
        maxLength,
        requireUppercase,
        requireLowercase,
        requireNumber,
        requireSpecialChar,
        customRegex,
        customRegexErrorMessage,
        maxHistory);
  }

  private static int getIntValue(Map<String, Object> map, String key, int defaultValue) {
    Object value = map.get(key);
    if (value instanceof Number) {
      return ((Number) value).intValue();
    }
    return defaultValue;
  }

  private static boolean getBooleanValue(
      Map<String, Object> map, String key, boolean defaultValue) {
    Object value = map.get(key);
    if (value instanceof Boolean) {
      return (Boolean) value;
    }
    return defaultValue;
  }

  private static String getStringValue(Map<String, Object> map, String key, String defaultValue) {
    Object value = map.get(key);
    if (value instanceof String) {
      return (String) value;
    }
    return defaultValue;
  }

  public int minLength() {
    return minLength;
  }

  public int maxLength() {
    return maxLength;
  }

  public boolean requireUppercase() {
    return requireUppercase;
  }

  public boolean requireLowercase() {
    return requireLowercase;
  }

  public boolean requireNumber() {
    return requireNumber;
  }

  public boolean requireSpecialChar() {
    return requireSpecialChar;
  }

  public String customRegex() {
    return customRegex;
  }

  public String customRegexErrorMessage() {
    return customRegexErrorMessage;
  }

  public int maxHistory() {
    return maxHistory;
  }

  /**
   * Converts this config to a Map for JSON serialization.
   *
   * @return map representation
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    map.put("min_length", minLength);
    map.put("max_length", maxLength);
    map.put("require_uppercase", requireUppercase);
    map.put("require_lowercase", requireLowercase);
    map.put("require_number", requireNumber);
    map.put("require_special_char", requireSpecialChar);
    if (customRegex != null) {
      map.put("custom_regex", customRegex);
    }
    if (customRegexErrorMessage != null) {
      map.put("custom_regex_error_message", customRegexErrorMessage);
    }
    map.put("max_history", maxHistory);
    return map;
  }

  public boolean exists() {
    return true;
  }
}
