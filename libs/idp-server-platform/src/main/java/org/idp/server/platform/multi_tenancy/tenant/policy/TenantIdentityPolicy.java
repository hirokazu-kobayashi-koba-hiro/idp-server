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
import org.idp.server.platform.multi_tenancy.tenant.TenantAttributes;

/**
 * Tenant user identity uniqueness policy
 *
 * <p>Defines which attribute should be used as the unique key for user identification within a
 * tenant.
 */
public class TenantIdentityPolicy {

  public TenantIdentityPolicy() {
    this.passwordPolicyConfig = PasswordPolicyConfig.defaultPolicy();
    this.authenticationDeviceRule = AuthenticationDeviceRule.defaultRule();
  }

  public enum UniqueKeyType {
    /** Use username as unique key */
    USERNAME,

    /**
     * Use username as unique key, fallback to external user ID if username is not available
     *
     * <p>This policy is useful for external identity providers that may not provide a preferred
     * username claim.
     */
    USERNAME_OR_EXTERNAL_USER_ID,

    /** Use email address as unique key */
    EMAIL,

    /**
     * Use email address as unique key, fallback to external user ID if email is not available
     *
     * <p>This is the recommended policy for external identity providers that may not always provide
     * email addresses (e.g., GitHub with private email, Twitter).
     *
     * <p>Issue #729: Supports multiple IdPs with same email while handling missing email cases.
     */
    EMAIL_OR_EXTERNAL_USER_ID,

    /** Use phone number as unique key */
    PHONE,

    /**
     * Use phone number as unique key, fallback to external user ID if phone is not available
     *
     * <p>This policy is useful for phone-based authentication with external providers.
     */
    PHONE_OR_EXTERNAL_USER_ID,

    /** Use external user ID as unique key */
    EXTERNAL_USER_ID
  }

  private UniqueKeyType uniqueKeyType;
  private PasswordPolicyConfig passwordPolicyConfig;
  private AuthenticationDeviceRule authenticationDeviceRule;

  public TenantIdentityPolicy(UniqueKeyType uniqueKeyType) {
    this.uniqueKeyType =
        uniqueKeyType != null ? uniqueKeyType : UniqueKeyType.EMAIL_OR_EXTERNAL_USER_ID;
    this.passwordPolicyConfig = PasswordPolicyConfig.defaultPolicy();
    this.authenticationDeviceRule = AuthenticationDeviceRule.defaultRule();
  }

  public TenantIdentityPolicy(
      UniqueKeyType uniqueKeyType, PasswordPolicyConfig passwordPolicyConfig) {
    this.uniqueKeyType =
        uniqueKeyType != null ? uniqueKeyType : UniqueKeyType.EMAIL_OR_EXTERNAL_USER_ID;
    this.passwordPolicyConfig =
        passwordPolicyConfig != null ? passwordPolicyConfig : PasswordPolicyConfig.defaultPolicy();
    this.authenticationDeviceRule = AuthenticationDeviceRule.defaultRule();
  }

  public TenantIdentityPolicy(
      UniqueKeyType uniqueKeyType,
      PasswordPolicyConfig passwordPolicyConfig,
      AuthenticationDeviceRule authenticationDeviceRule) {
    this.uniqueKeyType =
        uniqueKeyType != null ? uniqueKeyType : UniqueKeyType.EMAIL_OR_EXTERNAL_USER_ID;
    this.passwordPolicyConfig =
        passwordPolicyConfig != null ? passwordPolicyConfig : PasswordPolicyConfig.defaultPolicy();
    this.authenticationDeviceRule =
        authenticationDeviceRule != null
            ? authenticationDeviceRule
            : AuthenticationDeviceRule.defaultRule();
  }

  /**
   * Default policy: use EMAIL_OR_EXTERNAL_USER_ID as unique key
   *
   * <p>This policy uses email when available, but falls back to external_user_id when email is not
   * provided by the identity provider. This is the recommended setting for systems that integrate
   * with multiple external identity providers.
   *
   * <p>Issue #729: Changed from EMAIL to EMAIL_OR_EXTERNAL_USER_ID to support external IdPs that
   * don't always provide email addresses.
   */
  public static TenantIdentityPolicy defaultPolicy() {
    return new TenantIdentityPolicy(UniqueKeyType.EMAIL_OR_EXTERNAL_USER_ID);
  }

  /**
   * Constructs identity policy from map
   *
   * <p>Reads "identity_unique_key_type", "password_policy", and "authentication_device_rule" from
   * map. If not configured, defaults to EMAIL_OR_EXTERNAL_USER_ID, default password policy, and
   * default authentication device rule.
   *
   * @param map configuration map
   * @return identity policy
   */
  @SuppressWarnings("unchecked")
  public static TenantIdentityPolicy fromMap(Map<String, Object> map) {
    if (map == null || map.isEmpty()) {
      return defaultPolicy();
    }
    String keyTypeValue =
        (String) map.getOrDefault("identity_unique_key_type", "EMAIL_OR_EXTERNAL_USER_ID");
    UniqueKeyType uniqueKeyType;
    try {
      uniqueKeyType = UniqueKeyType.valueOf(keyTypeValue.toUpperCase());
    } catch (IllegalArgumentException e) {
      uniqueKeyType = UniqueKeyType.EMAIL_OR_EXTERNAL_USER_ID;
    }

    PasswordPolicyConfig passwordPolicyConfig = PasswordPolicyConfig.defaultPolicy();
    if (map.containsKey("password_policy")) {
      Map<String, Object> passwordPolicyMap = (Map<String, Object>) map.get("password_policy");
      passwordPolicyConfig = PasswordPolicyConfig.fromMap(passwordPolicyMap);
    }

    AuthenticationDeviceRule authenticationDeviceRule = AuthenticationDeviceRule.defaultRule();
    if (map.containsKey("authentication_device_rule")) {
      Map<String, Object> deviceRuleMap =
          (Map<String, Object>) map.get("authentication_device_rule");
      authenticationDeviceRule = AuthenticationDeviceRule.fromMap(deviceRuleMap);
    }

    return new TenantIdentityPolicy(uniqueKeyType, passwordPolicyConfig, authenticationDeviceRule);
  }

  /**
   * Constructs identity policy from tenant attributes
   *
   * <p>Reads "identity_unique_key_type" from tenant attributes. If not configured, defaults to
   * EMAIL_OR_EXTERNAL_USER_ID.
   *
   * <p>Note: Password policy should be configured via identity_policy_config JSONB column, not
   * tenant attributes.
   *
   * @param attributes tenant attributes
   * @return identity policy
   */
  public static TenantIdentityPolicy fromTenantAttributes(TenantAttributes attributes) {
    String keyTypeValue =
        attributes.optValueAsString("identity_unique_key_type", "EMAIL_OR_EXTERNAL_USER_ID");
    try {
      UniqueKeyType uniqueKeyType = UniqueKeyType.valueOf(keyTypeValue.toUpperCase());
      return new TenantIdentityPolicy(uniqueKeyType);
    } catch (IllegalArgumentException e) {
      return defaultPolicy();
    }
  }

  public UniqueKeyType uniqueKeyType() {
    return uniqueKeyType;
  }

  public PasswordPolicyConfig passwordPolicyConfig() {
    return passwordPolicyConfig != null
        ? passwordPolicyConfig
        : PasswordPolicyConfig.defaultPolicy();
  }

  /**
   * Returns authentication device rule for this tenant.
   *
   * <p>Defines maximum device limits and identity verification requirements.
   *
   * @return authentication device rule
   */
  public AuthenticationDeviceRule authenticationDeviceRule() {
    return authenticationDeviceRule != null
        ? authenticationDeviceRule
        : AuthenticationDeviceRule.defaultRule();
  }

  /**
   * Returns maximum number of authentication devices allowed per user.
   *
   * @return maximum device count
   */
  public int maxDevices() {
    return authenticationDeviceRule().maxDevices();
  }

  /**
   * Checks if identity verification is required for device registration.
   *
   * @return true if identity verification is required
   */
  public boolean requiresIdentityVerificationForDeviceRegistration() {
    return authenticationDeviceRule().requiredIdentityVerification();
  }

  /**
   * Checks if this policy exists (is not null).
   *
   * @return true if policy exists
   */
  public boolean exists() {
    return uniqueKeyType != null;
  }

  /**
   * Converts this policy to a Map for JSON serialization.
   *
   * <p>Returns a map with "identity_unique_key_type", "password_policy", and
   * "authentication_device_rule" keys for database storage.
   *
   * @return map representation
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    if (uniqueKeyType != null) {
      map.put("identity_unique_key_type", uniqueKeyType.name());
    }
    if (passwordPolicyConfig != null) {
      map.put("password_policy", passwordPolicyConfig.toMap());
    }
    if (authenticationDeviceRule != null) {
      map.put("authentication_device_rule", authenticationDeviceRule.toMap());
    }
    return map;
  }
}
