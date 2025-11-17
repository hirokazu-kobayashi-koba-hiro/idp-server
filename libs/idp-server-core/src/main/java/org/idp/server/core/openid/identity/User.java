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

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import org.idp.server.core.openid.identity.address.Address;
import org.idp.server.core.openid.identity.device.AuthenticationDevice;
import org.idp.server.core.openid.identity.device.AuthenticationDeviceIdentifier;
import org.idp.server.core.openid.identity.device.AuthenticationDevices;
import org.idp.server.core.openid.oauth.type.extension.CustomProperties;
import org.idp.server.core.openid.oauth.type.vc.Credential;
import org.idp.server.platform.date.SystemDateTime;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.json.JsonReadable;
import org.idp.server.platform.multi_tenancy.organization.OrganizationIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.uuid.UuidConvertable;

public class User implements JsonReadable, Serializable, UuidConvertable {
  String sub;
  String providerId;
  String externalUserId;
  HashMap<String, Object> externalProviderOriginalPayload;
  String name;
  String givenName;
  String familyName;
  String middleName;
  String nickname;
  String preferredUsername;
  String profile;
  String picture;
  String website;
  String email;
  Boolean emailVerified;
  String gender;
  String birthdate;
  String zoneinfo;
  String locale;
  String phoneNumber;
  Boolean phoneNumberVerified;
  Address address;
  LocalDateTime createdAt;
  LocalDateTime updatedAt;
  String hashedPassword;
  String rawPassword;
  List<AuthenticationDevice> authenticationDevices;
  HashMap<String, Object> customProperties;
  List<HashMap<String, Object>> credentials;
  List<UserRole> roles;
  List<String> permissions;
  String currentTenant;
  List<String> assignedTenants;
  String currentOrganizationId;
  List<String> assignedOrganizations;
  HashMap<String, Object> verifiedClaims;
  UserStatus status;

  public User() {}

  public User(
      String sub,
      String providerId,
      String externalUserId,
      HashMap<String, Object> externalProviderOriginalPayload,
      String name,
      String givenName,
      String familyName,
      String middleName,
      String nickname,
      String preferredUsername,
      String profile,
      String picture,
      String website,
      String email,
      Boolean emailVerified,
      String gender,
      String birthdate,
      String zoneinfo,
      String locale,
      String phoneNumber,
      Boolean phoneNumberVerified,
      Address address,
      LocalDateTime createdAt,
      LocalDateTime updatedAt,
      String hashedPassword,
      String rawPassword,
      List<AuthenticationDevice> authenticationDevices,
      HashMap<String, Object> customProperties,
      List<HashMap<String, Object>> credentials,
      List<UserRole> roles,
      List<String> permissions,
      String currentTenant,
      List<String> assignedTenants,
      String currentOrganizationId,
      List<String> assignedOrganizations,
      HashMap<String, Object> verifiedClaims,
      UserStatus status) {
    this.sub = sub;
    this.providerId = providerId;
    this.externalUserId = externalUserId;
    this.externalProviderOriginalPayload = externalProviderOriginalPayload;
    this.name = name;
    this.givenName = givenName;
    this.familyName = familyName;
    this.middleName = middleName;
    this.nickname = nickname;
    this.preferredUsername = preferredUsername;
    this.profile = profile;
    this.picture = picture;
    this.website = website;
    this.email = email;
    this.emailVerified = emailVerified;
    this.gender = gender;
    this.birthdate = birthdate;
    this.zoneinfo = zoneinfo;
    this.locale = locale;
    this.phoneNumber = phoneNumber;
    this.phoneNumberVerified = phoneNumberVerified;
    this.address = address;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
    this.hashedPassword = hashedPassword;
    this.rawPassword = rawPassword;
    this.authenticationDevices = authenticationDevices;
    this.customProperties = customProperties;
    this.credentials = credentials;
    this.roles = roles;
    this.permissions = permissions;
    this.currentTenant = currentTenant;
    this.assignedTenants = assignedTenants;
    this.currentOrganizationId = currentOrganizationId;
    this.assignedOrganizations = assignedOrganizations;
    this.verifiedClaims = verifiedClaims;
    this.status = status;
  }

  public static User notFound() {
    return new User();
  }

  public static User initialized() {
    User user = new User();
    user.providerId = "idp-server";
    user.status = UserStatus.INITIALIZED;
    user.externalProviderOriginalPayload = new HashMap<>();
    user.authenticationDevices = new ArrayList<>();
    user.customProperties = new HashMap<>();
    user.credentials = new ArrayList<>();
    user.roles = new ArrayList<>();
    user.permissions = new ArrayList<>();
    user.assignedTenants = new ArrayList<>();
    user.assignedOrganizations = new ArrayList<>();
    user.verifiedClaims = new HashMap<>();
    return user;
  }

  public boolean canTransit(UserStatus from, UserStatus to) {
    return UserLifecycleManager.canTransit(from, to);
  }

  public User transitStatus(UserStatus newStatus) {
    this.status = UserLifecycleManager.transit(this.status, newStatus);
    return this;
  }

  public UserIdentifier userIdentifier() {
    return new UserIdentifier(sub);
  }

  public String sub() {
    return sub;
  }

  public UUID subAsUuid() {
    return convertUuid(sub);
  }

  public User setSub(String sub) {
    this.sub = sub;
    return this;
  }

  public boolean hasSub() {
    return sub != null && !sub.isEmpty();
  }

  public String providerId() {
    return providerId;
  }

  public User setProviderId(String providerId) {
    this.providerId = providerId;
    return this;
  }

  public String externalUserId() {
    return externalUserId;
  }

  public User setExternalUserId(String externalUserId) {
    this.externalUserId = externalUserId;
    return this;
  }

  public User setExternalProviderOriginalPayload(
      HashMap<String, Object> externalProviderOriginalPayload) {
    this.externalProviderOriginalPayload = externalProviderOriginalPayload;
    return this;
  }

  public boolean hasExternalProviderOriginalPayload() {
    return externalProviderOriginalPayload != null && !externalProviderOriginalPayload.isEmpty();
  }

  public HashMap<String, Object> externalProviderOriginalPayload() {
    return externalProviderOriginalPayload;
  }

  public String name() {
    return name;
  }

  public User setName(String name) {
    this.name = name;
    return this;
  }

  public String givenName() {
    return givenName;
  }

  public User setGivenName(String givenName) {
    this.givenName = givenName;
    return this;
  }

  public String familyName() {
    return familyName;
  }

  public User setFamilyName(String familyName) {
    this.familyName = familyName;
    return this;
  }

  public String middleName() {
    return middleName;
  }

  public User setMiddleName(String middleName) {
    this.middleName = middleName;
    return this;
  }

  public String nickname() {
    return nickname;
  }

  public User setNickname(String nickname) {
    this.nickname = nickname;
    return this;
  }

  public String preferredUsername() {
    return preferredUsername;
  }

  public User setPreferredUsername(String preferredUsername) {
    this.preferredUsername = preferredUsername;
    return this;
  }

  /**
   * Applies tenant identity policy to set preferred_username automatically.
   *
   * <p>Sets the preferred_username field based on the tenant's unique key policy. The value is
   * normalized according to the policy type (username, email, phone, or external_user_id). For
   * fallback policies (e.g., EMAIL_OR_EXTERNAL_USER_ID), falls back to external_user_id when the
   * primary value is not available.
   *
   * <p>Issue #729: Added support for fallback policies to handle cases where external identity
   * providers don't provide certain claims (e.g., email).
   *
   * @param policy tenant identity policy
   * @return this user instance
   */
  public User applyIdentityPolicy(
      org.idp.server.platform.multi_tenancy.tenant.policy.TenantIdentityPolicy policy) {
    String value =
        switch (policy.uniqueKeyType()) {
          case USERNAME, USERNAME_OR_EXTERNAL_USER_ID -> this.name;
          case EMAIL, EMAIL_OR_EXTERNAL_USER_ID -> this.email;
          case PHONE, PHONE_OR_EXTERNAL_USER_ID -> this.phoneNumber;
          case EXTERNAL_USER_ID -> this.externalUserId;
        };

    // Fallback to external_user_id for policies with _OR_EXTERNAL_USER_ID suffix
    if ((value == null || value.isBlank()) && this.externalUserId != null) {
      boolean shouldFallback =
          switch (policy.uniqueKeyType()) {
            case USERNAME_OR_EXTERNAL_USER_ID,
                    EMAIL_OR_EXTERNAL_USER_ID,
                    PHONE_OR_EXTERNAL_USER_ID ->
                true;
            default -> false;
          };

      if (shouldFallback) {
        // For external providers, use "provider.external_user_id" format (Keycloak style)
        // For local provider (idp-server), use external_user_id as-is
        value =
            "idp-server".equals(this.providerId)
                ? this.externalUserId
                : this.providerId + "." + this.externalUserId;
      }
    }

    if (value != null && !value.isBlank()) {
      this.preferredUsername = value;
    }
    return this;
  }

  public String profile() {
    return profile;
  }

  public User setProfile(String profile) {
    this.profile = profile;
    return this;
  }

  public String picture() {
    return picture;
  }

  public User setPicture(String picture) {
    this.picture = picture;
    return this;
  }

  public String website() {
    return website;
  }

  public User setWebsite(String website) {
    this.website = website;
    return this;
  }

  public String email() {
    return email;
  }

  public User setEmail(String email) {
    this.email = email;
    return this;
  }

  public boolean emailVerified() {
    if (hasEmailVerified()) {
      return emailVerified;
    }
    return false;
  }

  public User setEmailVerified(boolean emailVerified) {
    this.emailVerified = emailVerified;
    return this;
  }

  public String gender() {
    return gender;
  }

  public User setGender(String gender) {
    this.gender = gender;
    return this;
  }

  public String birthdate() {
    return birthdate;
  }

  public User setBirthdate(String birthdate) {
    this.birthdate = birthdate;
    return this;
  }

  public String zoneinfo() {
    return zoneinfo;
  }

  public User setZoneinfo(String zoneinfo) {
    this.zoneinfo = zoneinfo;
    return this;
  }

  public String locale() {
    return locale;
  }

  public User setLocale(String locale) {
    this.locale = locale;
    return this;
  }

  public String phoneNumber() {
    return phoneNumber;
  }

  public User setPhoneNumber(String phoneNumber) {
    this.phoneNumber = phoneNumber;
    return this;
  }

  public boolean phoneNumberVerified() {
    if (hasPhoneNumberVerified()) {
      return phoneNumberVerified;
    }
    return false;
  }

  public User setPhoneNumberVerified(boolean phoneNumberVerified) {
    this.phoneNumberVerified = phoneNumberVerified;
    return this;
  }

  public Address address() {
    return address;
  }

  public User setAddress(Address address) {
    this.address = address;
    return this;
  }

  public LocalDateTime createdAt() {
    return createdAt;
  }

  public long createdAtAsLong() {
    return SystemDateTime.toEpochSecond(createdAt);
  }

  public User setCreatedAt(LocalDateTime createdAt) {
    this.createdAt = createdAt;
    return this;
  }

  public LocalDateTime updatedAt() {
    return updatedAt;
  }

  public long updateAtAsLong() {
    return SystemDateTime.toEpochSecond(updatedAt);
  }

  public String hashedPassword() {
    return hashedPassword;
  }

  public User setHashedPassword(String hashedPassword) {
    this.hashedPassword = hashedPassword;
    return this;
  }

  public boolean hasRawPassword() {
    return Objects.nonNull(rawPassword) && !rawPassword.isEmpty();
  }

  public boolean hasPassword() {
    return Objects.nonNull(hashedPassword) && !hashedPassword.isEmpty();
  }

  public User setUpdatedAt(LocalDateTime updatedAt) {
    this.updatedAt = updatedAt;
    return this;
  }

  public User setVerifiedClaims(HashMap<String, Object> verifiedClaims) {
    this.verifiedClaims = verifiedClaims;
    return this;
  }

  public User setVerifiedClaims(Map<String, Object> verifiedClaims) {
    this.verifiedClaims = new HashMap<>(verifiedClaims);
    return this;
  }

  // TODO to be more correct
  public User mergeVerifiedClaims(Map<String, Object> verifiedClaims) {
    this.verifiedClaims.putAll(verifiedClaims);
    return this;
  }

  public HashMap<String, Object> verifiedClaims() {
    return verifiedClaims;
  }

  public JsonNodeWrapper verifiedClaimsNodeWrapper() {
    return JsonNodeWrapper.fromMap(verifiedClaims);
  }

  public boolean hasVerifiedClaims() {
    return Objects.nonNull(verifiedClaims) && !verifiedClaims.isEmpty();
  }

  public AuthenticationDevices authenticationDevices() {
    return new AuthenticationDevices(authenticationDevices);
  }

  public AuthenticationDevice findPrimaryAuthenticationDevice() {
    return authenticationDevices.stream()
        .min(Comparator.comparingInt(AuthenticationDevice::priority))
        .orElse(new AuthenticationDevice());
  }

  public AuthenticationDevice findAuthenticationDevice(String deviceId) {
    return authenticationDevices.stream()
        .filter(authenticationDevice -> authenticationDevice.id().equals(deviceId))
        .findFirst()
        .orElse(new AuthenticationDevice());
  }

  public List<AuthenticationDevice> authenticationDevicesAsList() {
    return authenticationDevices;
  }

  public List<Map<String, Object>> authenticationDevicesListAsMap() {
    return authenticationDevices.stream().map(AuthenticationDevice::toMap).toList();
  }

  public User setAuthenticationDevices(List<AuthenticationDevice> authenticationDevices) {
    this.authenticationDevices = authenticationDevices;
    return this;
  }

  public User addAuthenticationDevice(AuthenticationDevice authenticationDevice) {
    this.authenticationDevices.add(authenticationDevice);
    return this;
  }

  public User patchWithAuthenticationDevice(AuthenticationDevice patchAuthenticationDevice) {
    AuthenticationDevice authenticationDevice =
        findAuthenticationDevice(patchAuthenticationDevice.id());
    if (authenticationDevice.exists()) {
      List<AuthenticationDevice> updated =
          new ArrayList<>(
              authenticationDevices.stream()
                  .filter(device -> !device.id().equals(authenticationDevice.id()))
                  .toList());
      AuthenticationDevice patched = authenticationDevice.patchWith(patchAuthenticationDevice);
      updated.add(patched);
      this.authenticationDevices = updated;
    }
    return this;
  }

  public boolean hasAuthenticationDevice(String deviceId) {
    return authenticationDevices.stream().anyMatch(device -> device.id().equals(deviceId));
  }

  public boolean hasAuthenticationDevice(AuthenticationDeviceIdentifier deviceId) {
    return authenticationDevices.stream().anyMatch(device -> device.id().equals(deviceId.value()));
  }

  public User removeAuthenticationDevice(String deviceId) {
    List<AuthenticationDevice> removed =
        authenticationDevices.stream().filter(device -> !device.id().equals(deviceId)).toList();
    this.authenticationDevices = removed;
    return this;
  }

  public User removeAllAuthenticationDevicesOfType(String authenticationType) {
    List<AuthenticationDevice> filtered =
        authenticationDevices.stream()
            .filter(device -> !device.availableMethods().contains(authenticationType))
            .collect(Collectors.toList());
    this.authenticationDevices = filtered;
    return this;
  }

  public int authenticationDeviceCount() {
    return authenticationDevices.size();
  }

  public int authenticationDeviceNextCount() {
    return authenticationDevices.size() + 1;
  }

  public boolean exists() {
    return Objects.nonNull(sub) && !sub.isEmpty();
  }

  public boolean hasExternalUserId() {
    return externalUserId != null && !externalUserId.isEmpty();
  }

  public boolean hasName() {
    return Objects.nonNull(name) && !name.isEmpty();
  }

  public boolean hasGivenName() {
    return Objects.nonNull(givenName) && !givenName.isEmpty();
  }

  public boolean hasFamilyName() {
    return Objects.nonNull(familyName) && !familyName.isEmpty();
  }

  public boolean hasMiddleName() {
    return Objects.nonNull(middleName) && !middleName.isEmpty();
  }

  public boolean hasNickname() {
    return Objects.nonNull(nickname) && !nickname.isEmpty();
  }

  public boolean hasPreferredUsername() {
    return Objects.nonNull(preferredUsername) && !preferredUsername.isEmpty();
  }

  public boolean hasProfile() {
    return Objects.nonNull(profile) && !profile.isEmpty();
  }

  public boolean hasPicture() {
    return Objects.nonNull(picture) && !picture.isEmpty();
  }

  public boolean hasWebsite() {
    return Objects.nonNull(website) && !website.isEmpty();
  }

  public boolean hasEmail() {
    return Objects.nonNull(email) && !email.isEmpty();
  }

  public boolean hasEmailVerified() {
    return Objects.nonNull(emailVerified);
  }

  public boolean hasGender() {
    return Objects.nonNull(gender) && !gender.isEmpty();
  }

  public boolean hasBirthdate() {
    return Objects.nonNull(birthdate) && !birthdate.isEmpty();
  }

  public boolean hasZoneinfo() {
    return Objects.nonNull(zoneinfo) && !zoneinfo.isEmpty();
  }

  public boolean hasLocale() {
    return Objects.nonNull(locale) && !locale.isEmpty();
  }

  public boolean hasPhoneNumber() {
    return Objects.nonNull(phoneNumber) && !phoneNumber.isEmpty();
  }

  public boolean hasPhoneNumberVerified() {
    return Objects.nonNull(phoneNumberVerified);
  }

  public boolean hasAddress() {
    return Objects.nonNull(address) && address.exists();
  }

  public boolean hasHashedPassword() {
    return Objects.nonNull(hashedPassword) && !hashedPassword.isEmpty();
  }

  public boolean hasCreatedAt() {
    return Objects.nonNull(createdAt);
  }

  public boolean hasUpdatedAt() {
    return Objects.nonNull(updatedAt);
  }

  public CustomProperties customProperties() {
    return new CustomProperties(customProperties);
  }

  public HashMap<String, Object> customPropertiesValue() {
    return customProperties;
  }

  public User setCustomProperties(HashMap<String, Object> customProperties) {
    this.customProperties = customProperties;
    return this;
  }

  public User addCustomProperties(HashMap<String, Object> customProperties) {
    HashMap<String, Object> newCustomProperties = new HashMap<>(this.customProperties);
    newCustomProperties.putAll(customProperties);
    this.customProperties = newCustomProperties;
    return this;
  }

  public boolean hasCustomProperties() {
    return !customProperties.isEmpty();
  }

  public User setCredentials(List<HashMap<String, Object>> credentials) {
    this.credentials = credentials;
    return this;
  }

  public List<Credential> verifiableCredentials() {
    return credentials.stream().map(Credential::new).toList();
  }

  public boolean hasCredentials() {
    return !credentials.isEmpty();
  }

  public List<UserRole> roles() {
    return roles;
  }

  public List<String> roleNameAsListString() {
    return roles.stream().map(UserRole::roleName).toList();
  }

  public List<Map<String, Object>> roleListAsMap() {
    return roles.stream().map(UserRole::toMap).toList();
  }

  public User setRoles(List<UserRole> roles) {
    this.roles = roles;
    return this;
  }

  public boolean hasRoles() {
    return roles != null && !roles.isEmpty();
  }

  public List<String> permissions() {
    return permissions;
  }

  public Set<String> permissionsAsSet() {
    return new HashSet<>(permissions);
  }

  public String permissionsAsString() {
    return String.join(",", permissions);
  }

  public User setPermissions(List<String> permissions) {
    this.permissions = permissions;
    return this;
  }

  public boolean hasPermissions() {
    return permissions != null && !permissions.isEmpty();
  }

  public boolean hasAuthenticationDevices() {
    return authenticationDevices != null && !authenticationDevices.isEmpty();
  }

  public String currentTenant() {
    return currentTenant;
  }

  public TenantIdentifier currentTenantIdentifier() {
    return new TenantIdentifier(currentTenant);
  }

  public boolean hasCurrentTenantId() {
    return currentTenant != null && !currentTenant.isEmpty();
  }

  public List<String> assignedTenants() {
    return assignedTenants;
  }

  public List<TenantIdentifier> assignedTenantsAsTenantIdentifiers() {
    return assignedTenants.stream().map(TenantIdentifier::new).toList();
  }

  public User setAssignedTenants(List<String> assignedTenants) {
    this.assignedTenants = assignedTenants;
    return this;
  }

  public User addAssignedTenant(TenantIdentifier tenantIdentifier) {
    List<String> newAssignedTenants = new ArrayList<>(this.assignedTenants);
    newAssignedTenants.add(tenantIdentifier.value());
    this.assignedTenants = newAssignedTenants;
    return this;
  }

  public User setCurrentTenantId(TenantIdentifier tenantIdentifier) {
    this.currentTenant = tenantIdentifier.value();
    return this;
  }

  public boolean hasAssignedTenants() {
    return assignedTenants != null && !assignedTenants.isEmpty();
  }

  public UserStatus status() {
    return status;
  }

  public String statusName() {
    return status.name();
  }

  public boolean hasStatus() {
    return status != null;
  }

  public User setStatus(UserStatus status) {
    this.status = status;
    return this;
  }

  public boolean hasCurrentOrganizationId() {
    return currentOrganizationId != null && !currentOrganizationId.isEmpty();
  }

  public OrganizationIdentifier currentOrganizationIdentifier() {
    return new OrganizationIdentifier(currentOrganizationId);
  }

  public User setCurrentOrganizationId(OrganizationIdentifier currentOrganizationId) {
    this.currentOrganizationId = currentOrganizationId.value();
    return this;
  }

  public List<String> assignedOrganizations() {
    return assignedOrganizations;
  }

  public boolean hasAssignedOrganizations() {
    return assignedOrganizations != null && !assignedOrganizations.isEmpty();
  }

  public User setAssignedOrganizations(List<String> assignedOrganizations) {
    this.assignedOrganizations = assignedOrganizations;
    return this;
  }

  public User addAssignedOrganizations(OrganizationIdentifier organizationIdentifier) {
    List<String> newAssignedOrganizations = new ArrayList<>(this.assignedOrganizations);
    newAssignedOrganizations.add(organizationIdentifier.value());
    this.assignedOrganizations = newAssignedOrganizations;
    return this;
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();

    if (exists()) map.put("sub", sub);
    if (exists()) map.put("provider_id", providerId);
    if (hasExternalUserId()) map.put("external_user_id", externalUserId);
    if (hasExternalProviderOriginalPayload())
      map.put("external_user_original_payload", externalProviderOriginalPayload);
    if (hasName()) map.put("name", name);
    if (hasGivenName()) map.put("given_name", givenName);
    if (hasFamilyName()) map.put("family_name", familyName);
    if (hasMiddleName()) map.put("middle_name", middleName);
    if (hasNickname()) map.put("nickname", nickname);
    if (hasPreferredUsername()) map.put("preferred_username", preferredUsername);
    if (hasProfile()) map.put("profile", profile);
    if (hasPicture()) map.put("picture", picture);
    if (hasWebsite()) map.put("website", website);
    if (hasEmail()) map.put("email", email);
    if (hasEmailVerified()) map.put("email_verified", emailVerified);
    if (hasGender()) map.put("gender", gender);
    if (hasBirthdate()) map.put("birthdate", birthdate);
    if (hasZoneinfo()) map.put("zoneinfo", zoneinfo);
    if (hasLocale()) map.put("locale", locale);
    if (hasPhoneNumber()) map.put("phone_number", phoneNumber);
    if (hasPhoneNumberVerified()) map.put("phone_number_verified", phoneNumberVerified);
    if (hasCreatedAt()) map.put("created_at", createdAt.toString());
    if (hasUpdatedAt()) map.put("updated_at", updatedAt.toString());
    if (hasAddress()) map.put("address", address.toMap());
    if (hasCustomProperties()) map.put("custom_properties", new HashMap<>(customProperties));
    if (hasHashedPassword()) map.put("hashed_password", "****");
    if (hasRoles()) map.put("roles", roleListAsMap());
    if (hasPermissions()) map.put("permissions", permissions);
    if (hasAuthenticationDevices())
      map.put("authentication_devices", authenticationDevicesListAsMap());
    if (hasCurrentTenantId()) map.put("current_tenant_id", currentTenant);
    if (hasAssignedTenants()) map.put("assigned_tenants", assignedTenants);
    if (hasCurrentOrganizationId()) map.put("current_organization_id", currentOrganizationId);
    if (hasAssignedOrganizations()) map.put("assigned_organizations", assignedOrganizations);
    if (hasVerifiedClaims()) map.put("verified_claims", verifiedClaims);
    if (exists()) map.put("status", status.name());

    return map;
  }

  public Map<String, Object> toMinimalizedMap() {
    Map<String, Object> map = new HashMap<>();

    if (exists()) map.put("sub", sub);
    if (exists()) map.put("provider_id", providerId);
    if (hasExternalUserId()) map.put("external_user_id", externalUserId);
    if (hasName()) map.put("name", name);
    if (hasEmail()) map.put("email", email);
    if (hasLocale()) map.put("locale", locale);
    if (hasPhoneNumber()) map.put("phone_number", phoneNumber);
    if (exists()) map.put("status", status.name());

    return map;
  }

  public Map<String, Object> toMaskedValueMap() {
    Map<String, Object> maskedMap = toMap();
    maskedMap.replaceAll((k, v) -> "****");

    return maskedMap;
  }

  public User didEmailVerification() {
    this.emailVerified = true;
    return this;
  }

  public String rawPassword() {
    return rawPassword;
  }

  public boolean isIdentityVerified() {
    return status.isIdentityVerified();
  }

  public boolean isActive() {
    return status.isActive();
  }

  public boolean enabledFidoUaf() {
    return authenticationDevices.stream().anyMatch(AuthenticationDevice::enabledFidoUaf);
  }

  public User updateWith(User patchUser) {
    return new User(
        this.sub, // sub is immutable
        this.providerId, // providerId is immutable
        this.externalUserId, // externalUserId is immutable
        patchUser.hasExternalProviderOriginalPayload()
            ? patchUser.externalProviderOriginalPayload()
            : this.externalProviderOriginalPayload,
        patchUser.hasName() ? patchUser.name() : this.name,
        patchUser.hasGivenName() ? patchUser.givenName() : this.givenName,
        patchUser.hasFamilyName() ? patchUser.familyName() : this.familyName,
        patchUser.hasMiddleName() ? patchUser.middleName() : this.middleName,
        patchUser.hasNickname() ? patchUser.nickname() : this.nickname,
        patchUser.hasPreferredUsername() ? patchUser.preferredUsername() : this.preferredUsername,
        patchUser.hasProfile() ? patchUser.profile() : this.profile,
        patchUser.hasPicture() ? patchUser.picture() : this.picture,
        patchUser.hasWebsite() ? patchUser.website() : this.website,
        patchUser.hasEmail() ? patchUser.email() : this.email,
        patchUser.hasEmailVerified() ? patchUser.emailVerified : this.emailVerified,
        patchUser.hasGender() ? patchUser.gender() : this.gender,
        patchUser.hasBirthdate() ? patchUser.birthdate() : this.birthdate,
        patchUser.hasZoneinfo() ? patchUser.zoneinfo() : this.zoneinfo,
        patchUser.hasLocale() ? patchUser.locale() : this.locale,
        patchUser.hasPhoneNumber() ? patchUser.phoneNumber() : this.phoneNumber,
        patchUser.hasPhoneNumberVerified()
            ? patchUser.phoneNumberVerified
            : this.phoneNumberVerified,
        patchUser.hasAddress() ? patchUser.address() : this.address,
        this.createdAt, // createdAt is immutable
        patchUser.hasUpdatedAt() ? patchUser.updatedAt() : this.updatedAt,
        this.hashedPassword, // hashedPassword should not be updated via patch
        this.rawPassword, // rawPassword should not be updated via patch
        patchUser.hasAuthenticationDevices()
            ? patchUser.authenticationDevicesAsList()
            : this.authenticationDevices,
        patchUser.hasCustomProperties() ? patchUser.customPropertiesValue() : this.customProperties,
        this.credentials, // credentials are not patchable via this method
        patchUser.hasRoles() ? patchUser.roles() : this.roles,
        this.permissions, // permissions are derived from roles
        this.currentTenant, // currentTenant should not be updated via patch
        patchUser.hasAssignedTenants() ? patchUser.assignedTenants() : this.assignedTenants,
        this.currentOrganizationId, // currentOrganizationId should not be updated via patch
        this.assignedOrganizations, // assignedOrganizations should not be updated via patch
        this.verifiedClaims, // verifiedClaims should not be updated via patch
        patchUser.hasStatus() ? patchUser.status() : this.status);
  }
}
