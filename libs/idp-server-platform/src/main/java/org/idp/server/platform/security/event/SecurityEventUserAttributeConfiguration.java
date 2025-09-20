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

import org.idp.server.platform.multi_tenancy.tenant.TenantAttributes;

public class SecurityEventUserAttributeConfiguration {
  private static final String PREFIX = "security_event_user_";

  private final TenantAttributes tenantAttributes;

  public static SecurityEventUserAttributeConfiguration fromTenantAttributes(
      TenantAttributes attributes) {
    return new SecurityEventUserAttributeConfiguration(attributes);
  }

  private SecurityEventUserAttributeConfiguration(TenantAttributes tenantAttributes) {
    this.tenantAttributes = tenantAttributes;
  }

  public boolean isIncludeId() {
    return tenantAttributes.optValueAsBoolean(PREFIX + "include_id", true);
  }

  public boolean isIncludeName() {
    return tenantAttributes.optValueAsBoolean(PREFIX + "include_name", false);
  }

  public boolean isIncludeExternalUserId() {
    return tenantAttributes.optValueAsBoolean(PREFIX + "include_external_user_id", true);
  }

  public boolean isIncludeEmail() {
    return tenantAttributes.optValueAsBoolean(PREFIX + "include_email", false);
  }

  public boolean isIncludePhoneNumber() {
    return tenantAttributes.optValueAsBoolean(PREFIX + "include_phone_number", false);
  }

  public boolean isIncludeGivenName() {
    return tenantAttributes.optValueAsBoolean(PREFIX + "include_given_name", false);
  }

  public boolean isIncludeFamilyName() {
    return tenantAttributes.optValueAsBoolean(PREFIX + "include_family_name", false);
  }

  public boolean isIncludePreferredUsername() {
    return tenantAttributes.optValueAsBoolean(PREFIX + "include_preferred_username", false);
  }

  public boolean isIncludeProfile() {
    return tenantAttributes.optValueAsBoolean(PREFIX + "include_profile", false);
  }

  public boolean isIncludePicture() {
    return tenantAttributes.optValueAsBoolean(PREFIX + "include_picture", false);
  }

  public boolean isIncludeWebsite() {
    return tenantAttributes.optValueAsBoolean(PREFIX + "include_website", false);
  }

  public boolean isIncludeGender() {
    return tenantAttributes.optValueAsBoolean(PREFIX + "include_gender", false);
  }

  public boolean isIncludeBirthdate() {
    return tenantAttributes.optValueAsBoolean(PREFIX + "include_birthdate", false);
  }

  public boolean isIncludeZoneinfo() {
    return tenantAttributes.optValueAsBoolean(PREFIX + "include_zoneinfo", false);
  }

  public boolean isIncludeLocale() {
    return tenantAttributes.optValueAsBoolean(PREFIX + "include_locale", false);
  }

  public boolean isIncludeAddress() {
    return tenantAttributes.optValueAsBoolean(PREFIX + "include_address", false);
  }

  public boolean isIncludeRoles() {
    return tenantAttributes.optValueAsBoolean(PREFIX + "include_roles", false);
  }

  public boolean isIncludePermissions() {
    return tenantAttributes.optValueAsBoolean(PREFIX + "include_permissions", false);
  }

  public boolean isIncludeCurrentTenant() {
    return tenantAttributes.optValueAsBoolean(PREFIX + "include_current_tenant", false);
  }

  public boolean isIncludeAssignedTenants() {
    return tenantAttributes.optValueAsBoolean(PREFIX + "include_assigned_tenants", false);
  }

  public boolean isIncludeVerifiedClaims() {
    return tenantAttributes.optValueAsBoolean(PREFIX + "include_verified_claims", false);
  }
}
