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

package org.idp.server.core.openid.identity;

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

  public enum UniqueKeyType {
    /** Use username as unique key */
    USERNAME,

    /** Use email address as unique key */
    EMAIL,

    /** Use phone number as unique key */
    PHONE,

    /** Use external user ID as unique key */
    EXTERNAL_USER_ID
  }

  private final UniqueKeyType uniqueKeyType;

  public TenantIdentityPolicy(UniqueKeyType uniqueKeyType) {
    this.uniqueKeyType = uniqueKeyType != null ? uniqueKeyType : UniqueKeyType.EMAIL;
  }

  /** Default policy: use EMAIL as unique key */
  public static TenantIdentityPolicy defaultPolicy() {
    return new TenantIdentityPolicy(UniqueKeyType.EMAIL);
  }

  /**
   * Constructs identity policy from tenant attributes
   *
   * <p>Reads "identity_unique_key_type" from tenant attributes. If not configured, defaults to
   * EMAIL.
   *
   * @param attributes tenant attributes
   * @return identity policy
   */
  public static TenantIdentityPolicy fromTenantAttributes(TenantAttributes attributes) {
    String keyTypeValue = attributes.optValueAsString("identity_unique_key_type", "EMAIL");
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
   * <p>Returns a map with "identity_unique_key_type" key for database storage.
   *
   * @return map representation
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    if (uniqueKeyType != null) {
      map.put("identity_unique_key_type", uniqueKeyType.name());
    }
    return map;
  }

  /**
   * Extracts the value to be set as preferred_username from user.
   *
   * <p>Based on the unique key type, extracts and normalizes the appropriate user attribute.
   *
   * @param user the user
   * @return normalized value for preferred_username
   */
  public String extractPreferredUsername(User user) {
    return switch (uniqueKeyType) {
      case USERNAME -> normalizeUsername(user.preferredUsername());
      case EMAIL -> normalizeEmail(user.email());
      case PHONE -> normalizePhone(user.phoneNumber());
      case EXTERNAL_USER_ID -> normalizeExternalUserId(user.externalUserId());
    };
  }

  /**
   * Normalizes username (lowercase and trim).
   *
   * @param username the username
   * @return normalized username
   */
  private String normalizeUsername(String username) {
    if (username == null || username.isBlank()) {
      return null;
    }
    return username.trim().toLowerCase();
  }

  /**
   * Normalizes email address (lowercase and trim).
   *
   * @param email the email address
   * @return normalized email address
   */
  private String normalizeEmail(String email) {
    if (email == null || email.isBlank()) {
      return null;
    }
    return email.trim().toLowerCase();
  }

  /**
   * Normalizes phone number (extract digits only).
   *
   * <p>TODO: Implement E.164 format conversion
   *
   * @param phone the phone number
   * @return normalized phone number
   */
  private String normalizePhone(String phone) {
    if (phone == null || phone.isBlank()) {
      return null;
    }
    // Remove non-digit characters
    String digits = phone.replaceAll("[^0-9]", "");
    return digits.isEmpty() ? null : digits;
  }

  /**
   * Normalizes external user ID (trim).
   *
   * @param externalUserId the external user ID
   * @return normalized external user ID
   */
  private String normalizeExternalUserId(String externalUserId) {
    if (externalUserId == null || externalUserId.isBlank()) {
      return null;
    }
    return externalUserId.trim();
  }
}
