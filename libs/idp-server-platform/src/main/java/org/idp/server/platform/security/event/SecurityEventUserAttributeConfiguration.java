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

package org.idp.server.platform.security.event;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class SecurityEventUserAttributeConfiguration {
  private static final String PREFIX = "security_event_user_";

  private final boolean includeId;
  private final boolean includeName;
  private final boolean includeExternalUserId;
  private final boolean includeEmail;
  private final boolean includePhoneNumber;
  private final boolean includeGivenName;
  private final boolean includeFamilyName;
  private final boolean includePreferredUsername;
  private final boolean includeProfile;
  private final boolean includePicture;
  private final boolean includeWebsite;
  private final boolean includeGender;
  private final boolean includeBirthdate;
  private final boolean includeZoneinfo;
  private final boolean includeLocale;
  private final boolean includeAddress;
  private final boolean includeRoles;
  private final boolean includePermissions;
  private final boolean includeCurrentTenant;
  private final boolean includeAssignedTenants;
  private final boolean includeVerifiedClaims;

  public SecurityEventUserAttributeConfiguration() {
    this.includeId = true;
    this.includeName = false;
    this.includeExternalUserId = true;
    this.includeEmail = false;
    this.includePhoneNumber = false;
    this.includeGivenName = false;
    this.includeFamilyName = false;
    this.includePreferredUsername = false;
    this.includeProfile = false;
    this.includePicture = false;
    this.includeWebsite = false;
    this.includeGender = false;
    this.includeBirthdate = false;
    this.includeZoneinfo = false;
    this.includeLocale = false;
    this.includeAddress = false;
    this.includeRoles = false;
    this.includePermissions = false;
    this.includeCurrentTenant = false;
    this.includeAssignedTenants = false;
    this.includeVerifiedClaims = false;
  }

  public SecurityEventUserAttributeConfiguration(Map<String, Object> values) {
    Map<String, Object> safeValues = Objects.requireNonNullElseGet(values, HashMap::new);
    this.includeId = extractBoolean(safeValues, PREFIX + "include_id", true);
    this.includeName = extractBoolean(safeValues, PREFIX + "include_name", false);
    this.includeExternalUserId =
        extractBoolean(safeValues, PREFIX + "include_external_user_id", true);
    this.includeEmail = extractBoolean(safeValues, PREFIX + "include_email", false);
    this.includePhoneNumber = extractBoolean(safeValues, PREFIX + "include_phone_number", false);
    this.includeGivenName = extractBoolean(safeValues, PREFIX + "include_given_name", false);
    this.includeFamilyName = extractBoolean(safeValues, PREFIX + "include_family_name", false);
    this.includePreferredUsername =
        extractBoolean(safeValues, PREFIX + "include_preferred_username", false);
    this.includeProfile = extractBoolean(safeValues, PREFIX + "include_profile", false);
    this.includePicture = extractBoolean(safeValues, PREFIX + "include_picture", false);
    this.includeWebsite = extractBoolean(safeValues, PREFIX + "include_website", false);
    this.includeGender = extractBoolean(safeValues, PREFIX + "include_gender", false);
    this.includeBirthdate = extractBoolean(safeValues, PREFIX + "include_birthdate", false);
    this.includeZoneinfo = extractBoolean(safeValues, PREFIX + "include_zoneinfo", false);
    this.includeLocale = extractBoolean(safeValues, PREFIX + "include_locale", false);
    this.includeAddress = extractBoolean(safeValues, PREFIX + "include_address", false);
    this.includeRoles = extractBoolean(safeValues, PREFIX + "include_roles", false);
    this.includePermissions = extractBoolean(safeValues, PREFIX + "include_permissions", false);
    this.includeCurrentTenant =
        extractBoolean(safeValues, PREFIX + "include_current_tenant", false);
    this.includeAssignedTenants =
        extractBoolean(safeValues, PREFIX + "include_assigned_tenants", false);
    this.includeVerifiedClaims =
        extractBoolean(safeValues, PREFIX + "include_verified_claims", false);
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    map.put(PREFIX + "include_id", includeId);
    map.put(PREFIX + "include_name", includeName);
    map.put(PREFIX + "include_external_user_id", includeExternalUserId);
    map.put(PREFIX + "include_email", includeEmail);
    map.put(PREFIX + "include_phone_number", includePhoneNumber);
    map.put(PREFIX + "include_given_name", includeGivenName);
    map.put(PREFIX + "include_family_name", includeFamilyName);
    map.put(PREFIX + "include_preferred_username", includePreferredUsername);
    map.put(PREFIX + "include_profile", includeProfile);
    map.put(PREFIX + "include_picture", includePicture);
    map.put(PREFIX + "include_website", includeWebsite);
    map.put(PREFIX + "include_gender", includeGender);
    map.put(PREFIX + "include_birthdate", includeBirthdate);
    map.put(PREFIX + "include_zoneinfo", includeZoneinfo);
    map.put(PREFIX + "include_locale", includeLocale);
    map.put(PREFIX + "include_address", includeAddress);
    map.put(PREFIX + "include_roles", includeRoles);
    map.put(PREFIX + "include_permissions", includePermissions);
    map.put(PREFIX + "include_current_tenant", includeCurrentTenant);
    map.put(PREFIX + "include_assigned_tenants", includeAssignedTenants);
    map.put(PREFIX + "include_verified_claims", includeVerifiedClaims);
    return map;
  }

  public boolean exists() {
    return !includeId
        || includeName
        || !includeExternalUserId
        || includeEmail
        || includePhoneNumber
        || includeGivenName
        || includeFamilyName
        || includePreferredUsername
        || includeProfile
        || includePicture
        || includeWebsite
        || includeGender
        || includeBirthdate
        || includeZoneinfo
        || includeLocale
        || includeAddress
        || includeRoles
        || includePermissions
        || includeCurrentTenant
        || includeAssignedTenants
        || includeVerifiedClaims;
  }

  public boolean isIncludeId() {
    return includeId;
  }

  public boolean isIncludeName() {
    return includeName;
  }

  public boolean isIncludeExternalUserId() {
    return includeExternalUserId;
  }

  public boolean isIncludeEmail() {
    return includeEmail;
  }

  public boolean isIncludePhoneNumber() {
    return includePhoneNumber;
  }

  public boolean isIncludeGivenName() {
    return includeGivenName;
  }

  public boolean isIncludeFamilyName() {
    return includeFamilyName;
  }

  public boolean isIncludePreferredUsername() {
    return includePreferredUsername;
  }

  public boolean isIncludeProfile() {
    return includeProfile;
  }

  public boolean isIncludePicture() {
    return includePicture;
  }

  public boolean isIncludeWebsite() {
    return includeWebsite;
  }

  public boolean isIncludeGender() {
    return includeGender;
  }

  public boolean isIncludeBirthdate() {
    return includeBirthdate;
  }

  public boolean isIncludeZoneinfo() {
    return includeZoneinfo;
  }

  public boolean isIncludeLocale() {
    return includeLocale;
  }

  public boolean isIncludeAddress() {
    return includeAddress;
  }

  public boolean isIncludeRoles() {
    return includeRoles;
  }

  public boolean isIncludePermissions() {
    return includePermissions;
  }

  public boolean isIncludeCurrentTenant() {
    return includeCurrentTenant;
  }

  public boolean isIncludeAssignedTenants() {
    return includeAssignedTenants;
  }

  public boolean isIncludeVerifiedClaims() {
    return includeVerifiedClaims;
  }

  private static boolean extractBoolean(
      Map<String, Object> values, String key, boolean defaultValue) {
    if (values == null || values.isEmpty() || !values.containsKey(key)) {
      return defaultValue;
    }
    Object value = values.get(key);
    if (value instanceof Boolean) {
      return (Boolean) value;
    }
    return defaultValue;
  }
}
