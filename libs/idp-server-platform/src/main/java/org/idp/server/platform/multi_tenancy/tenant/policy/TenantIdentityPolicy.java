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

  public TenantIdentityPolicy() {}

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

  private UniqueKeyType uniqueKeyType;

  public TenantIdentityPolicy(UniqueKeyType uniqueKeyType) {
    this.uniqueKeyType = uniqueKeyType != null ? uniqueKeyType : UniqueKeyType.EMAIL;
  }

  /** Default policy: use EMAIL as unique key */
  public static TenantIdentityPolicy defaultPolicy() {
    return new TenantIdentityPolicy(UniqueKeyType.EMAIL);
  }

  /**
   * Constructs identity policy from map
   *
   * <p>Reads "identity_unique_key_type" from map. If not configured, defaults to EMAIL.
   *
   * @param map configuration map
   * @return identity policy
   */
  public static TenantIdentityPolicy fromMap(Map<String, Object> map) {
    if (map == null || map.isEmpty()) {
      return defaultPolicy();
    }
    String keyTypeValue = (String) map.getOrDefault("identity_unique_key_type", "EMAIL");
    try {
      UniqueKeyType uniqueKeyType = UniqueKeyType.valueOf(keyTypeValue.toUpperCase());
      return new TenantIdentityPolicy(uniqueKeyType);
    } catch (IllegalArgumentException e) {
      return defaultPolicy();
    }
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
   * Returns the value as-is without normalization.
   *
   * <p>Normalization removed to preserve original values from external systems. Username, email,
   * phone, and external_user_id are stored exactly as provided.
   *
   * @param value the value
   * @return the value unchanged (null if blank)
   */
  public String normalize(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value;
  }
}
