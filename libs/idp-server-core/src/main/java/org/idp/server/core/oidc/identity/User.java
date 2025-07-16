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

package org.idp.server.core.oidc.identity;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.*;
import org.idp.server.core.oidc.identity.address.Address;
import org.idp.server.core.oidc.identity.device.AuthenticationDevice;
import org.idp.server.core.oidc.identity.device.AuthenticationDevices;
import org.idp.server.core.oidc.type.extension.CustomProperties;
import org.idp.server.core.oidc.type.vc.Credential;
import org.idp.server.platform.date.SystemDateTime;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.json.JsonReadable;
import org.idp.server.platform.multi_tenancy.organization.OrganizationIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.uuid.UuidConvertable;

public class User implements JsonReadable, Serializable, UuidConvertable {
  String sub;
  String providerId = "idp-server";
  String externalUserId;
  HashMap<String, Object> externalProviderOriginalPayload = new HashMap<>();
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
  LocalDateTime updatedAt;
  String hashedPassword;
  String rawPassword;
  List<AuthenticationDevice> authenticationDevices = new ArrayList<>();
  HashMap<String, Object> customProperties = new HashMap<>();
  List<HashMap<String, Object>> credentials = new ArrayList<>();
  HashMap<String, Object> multiFactorAuthentication = new HashMap<>();
  List<UserRole> roles = new ArrayList<>();
  List<String> permissions = new ArrayList<>();
  String currentTenant;
  List<String> assignedTenants = new ArrayList<>();
  String currentOrganizationId;
  List<String> assignedOrganizations = new ArrayList<>();
  HashMap<String, Object> verifiedClaims = new HashMap<>();
  UserStatus status = UserStatus.UNREGISTERED;

  public static User notFound() {
    return new User();
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

  public boolean hasProviderOriginalPayload() {
    return externalProviderOriginalPayload != null && !externalProviderOriginalPayload.isEmpty();
  }

  public HashMap<String, Object> providerOriginalPayload() {
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

  public LocalDateTime updatedAt() {
    return updatedAt;
  }

  public long updateAtAsLong() {
    return updatedAt.toEpochSecond(SystemDateTime.zoneOffset);
  }

  public String hashedPassword() {
    return hashedPassword;
  }

  public User setHashedPassword(String hashedPassword) {
    this.hashedPassword = hashedPassword;
    return this;
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

  public List<AuthenticationDevice> authenticationDevicesAsList() {
    return authenticationDevices;
  }

  public AuthenticationDevice findPreferredForNotification() {
    return authenticationDevices.stream()
        .min(Comparator.comparingInt(AuthenticationDevice::priority))
        .orElse(new AuthenticationDevice());
  }

  public User setAuthenticationDevices(List<AuthenticationDevice> authenticationDevices) {
    this.authenticationDevices = authenticationDevices;
    return this;
  }

  public User addAuthenticationDevice(AuthenticationDevice authenticationDevice) {
    this.authenticationDevices.add(authenticationDevice);
    return this;
  }

  public boolean hasAuthenticationDevice(String deviceId) {
    return authenticationDevices.stream().anyMatch(device -> device.id().equals(deviceId));
  }

  public User removeAuthenticationDevice(String deviceId) {
    List<AuthenticationDevice> removed =
        authenticationDevices.stream().filter(device -> !device.id().equals(deviceId)).toList();
    this.authenticationDevices = removed;
    if (removed.isEmpty()) {
      multiFactorAuthentication.remove("fido-uaf");
    }
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

  public HashMap<String, Object> multiFactorAuthentication() {
    return multiFactorAuthentication;
  }

  public User setMultiFactorAuthentication(HashMap<String, Object> multiFactorAuthentication) {
    this.multiFactorAuthentication = multiFactorAuthentication;
    return this;
  }

  public User addMultiFactorAuthentication(HashMap<String, Object> multiFactorAuthentication) {
    HashMap<String, Object> newMfa = new HashMap<>(multiFactorAuthentication);
    newMfa.putAll(multiFactorAuthentication);
    this.multiFactorAuthentication = newMfa;
    return this;
  }

  public boolean hasMultiFactorAuthentication() {
    return multiFactorAuthentication != null && !multiFactorAuthentication.isEmpty();
  }

  public List<UserRole> roles() {
    return roles;
  }

  public List<String> roleNameAsListString() {
    return roles.stream().map(UserRole::roleName).toList();
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
    if (hasProviderOriginalPayload())
      map.put("provider_original_payload", externalProviderOriginalPayload);
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
    if (hasUpdatedAt()) map.put("updated_at", updatedAt.toString());
    if (hasAddress()) map.put("address", address.toMap());
    if (hasCustomProperties()) map.put("custom_properties", new HashMap<>(customProperties));
    if (hasHashedPassword()) map.put("hashed_password", "****");
    if (hasMultiFactorAuthentication())
      map.put("multi_factor_authentication", multiFactorAuthentication);
    if (hasRoles()) map.put("roles", roles);
    if (hasPermissions()) map.put("permissions", permissions);
    if (hasAuthenticationDevices()) map.put("authentication_devices", authenticationDevices);
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

  public boolean enabledFidoUaf() {
    if (!hasMultiFactorAuthentication()) {
      return false;
    }
    if (multiFactorAuthentication.containsKey("fido_uaf")) {
      return (boolean) multiFactorAuthentication.get("fido_uaf");
    }
    return false;
  }

  public boolean isIdentityVerified() {
    return status.isIdentityVerified();
  }
}
