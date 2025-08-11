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

package org.idp.server.core.adapters.datasource.identity;

import java.util.*;
import java.util.stream.Collectors;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.UserIdentifier;
import org.idp.server.core.openid.identity.UserRole;
import org.idp.server.core.openid.identity.UserStatus;
import org.idp.server.core.openid.identity.address.Address;
import org.idp.server.core.openid.identity.device.AuthenticationDevice;
import org.idp.server.platform.date.LocalDateTimeParser;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.multi_tenancy.organization.OrganizationIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;

class ModelConverter {

  static JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();

  static User convert(Map<String, String> stringMap) {
    User user = new User();

    user.setSub(stringMap.getOrDefault("id", ""));
    user.setProviderId(stringMap.getOrDefault("provider_id", ""));
    user.setExternalUserId(stringMap.getOrDefault("external_user_id", ""));
    user.setName(stringMap.getOrDefault("name", ""));
    user.setGivenName(stringMap.getOrDefault("given_name", ""));
    user.setFamilyName(stringMap.getOrDefault("family_name", ""));
    user.setMiddleName(stringMap.getOrDefault("middle_name", ""));
    user.setNickname(stringMap.getOrDefault("nickname", ""));
    user.setPreferredUsername(stringMap.getOrDefault("preferred_username", ""));
    user.setProfile(stringMap.getOrDefault("profile", ""));
    user.setPicture(stringMap.getOrDefault("picture", ""));
    user.setWebsite(stringMap.getOrDefault("website", ""));
    user.setEmail(stringMap.getOrDefault("email", ""));
    user.setEmailVerified(Boolean.parseBoolean(stringMap.getOrDefault("email_verified", "false")));
    user.setGender(stringMap.getOrDefault("gender", ""));
    user.setBirthdate(stringMap.getOrDefault("birthdate", ""));
    user.setZoneinfo(stringMap.getOrDefault("zoneinfo", ""));
    user.setLocale(stringMap.getOrDefault("locale", ""));
    user.setPhoneNumber(stringMap.getOrDefault("phone_number", ""));
    user.setPhoneNumberVerified(
        Boolean.parseBoolean(stringMap.getOrDefault("phone_number_verified", "false")));
    user.setHashedPassword(stringMap.getOrDefault("hashed_password", ""));

    if (stringMap.containsKey("updated_at")) {
      user.setUpdatedAt(LocalDateTimeParser.parse(stringMap.get("updated_at")));
    }

    if (stringMap.containsKey("address")
        && stringMap.get("address") != null
        && !stringMap.get("address").isEmpty()) {
      Address address = jsonConverter.read(stringMap.get("address"), Address.class);
      user.setAddress(address);
    }

    if (stringMap.containsKey("custom_properties")
        && stringMap.get("custom_properties") != null
        && !stringMap.get("custom_properties").isEmpty()) {
      JsonNodeWrapper jsonNodeWrapper =
          JsonNodeWrapper.fromString(stringMap.get("custom_properties"));
      HashMap<String, Object> customProps = new HashMap<>(jsonNodeWrapper.toMap());
      user.setCustomProperties(customProps);
    }

    if (stringMap.containsKey("credentials")
        && stringMap.get("credentials") != null
        && !stringMap.get("credentials").isEmpty()) {
      List<HashMap<String, Object>> credentials =
          jsonConverter.read(stringMap.get("credentials"), List.class);
      user.setCredentials(credentials);
    }
    if (stringMap.containsKey("roles")
        && stringMap.get("roles") != null
        && !stringMap.get("roles").equals("[]")) {
      JsonNodeWrapper jsonNodeWrapper = JsonNodeWrapper.fromString(stringMap.get("roles"));
      Collection<UserRole> distinctRoles =
          jsonNodeWrapper.elements().stream()
              .map(
                  node ->
                      new UserRole(
                          node.getValueOrEmptyAsString("role_id"),
                          node.getValueOrEmptyAsString("role_name")))
              .collect(
                  Collectors.toCollection(
                      () -> new TreeSet<>(Comparator.comparing(UserRole::roleId))));
      List<UserRole> roles = new ArrayList<>(distinctRoles);
      user.setRoles(roles);
    }

    if (stringMap.containsKey("permissions")
        && stringMap.get("permissions") != null
        && !stringMap.get("permissions").equals("[]")) {
      JsonNodeWrapper jsonNodeWrapper = JsonNodeWrapper.fromString(stringMap.get("permissions"));
      List<String> permissions = jsonNodeWrapper.toList();
      List<String> filtered = permissions.stream().filter(Objects::nonNull).toList();
      user.setPermissions(filtered);
    }

    if (stringMap.containsKey("assigned_tenants")
        && stringMap.get("assigned_tenants") != null
        && !stringMap.get("assigned_tenants").equals("[]")) {
      JsonNodeWrapper jsonNodeWrapper =
          JsonNodeWrapper.fromString(stringMap.get("assigned_tenants"));
      List<String> assignedTenants = jsonNodeWrapper.toList();
      List<String> filtered = assignedTenants.stream().filter(Objects::nonNull).toList();
      user.setAssignedTenants(filtered);
    }

    if (stringMap.containsKey("current_tenant_id")
        && stringMap.get("current_tenant_id") != null
        && !stringMap.get("current_tenant_id").isEmpty()) {
      String tenant = stringMap.get("current_tenant_id");
      user.setCurrentTenantId(new TenantIdentifier(tenant));
    }

    if (stringMap.containsKey("assigned_organizations")
        && stringMap.get("assigned_organizations") != null
        && !stringMap.get("assigned_organizations").equals("[]")) {
      JsonNodeWrapper jsonNodeWrapper =
          JsonNodeWrapper.fromString(stringMap.get("assigned_organizations"));
      List<String> assignedTenants = jsonNodeWrapper.toList();
      List<String> filtered = assignedTenants.stream().filter(Objects::nonNull).toList();
      user.setAssignedOrganizations(filtered);
    }

    if (stringMap.containsKey("current_organization_id")
        && stringMap.get("current_organization_id") != null
        && !stringMap.get("current_organization_id").isEmpty()) {
      String currentOrganization = stringMap.get("current_organization_id");
      user.setCurrentOrganizationId(new OrganizationIdentifier(currentOrganization));
    }

    if (stringMap.containsKey("authentication_devices")
        && stringMap.get("authentication_devices") != null
        && !stringMap.get("authentication_devices").equals("[]")) {
      JsonNodeWrapper jsonNodeWrapper =
          jsonConverter.readTree(stringMap.get("authentication_devices"));
      List<JsonNodeWrapper> wrapperList = jsonNodeWrapper.elements();
      List<AuthenticationDevice> authenticationDevices = new ArrayList<>();
      for (JsonNodeWrapper wrapper : wrapperList) {
        String id = wrapper.getValueOrEmptyAsString("id");
        String appName = wrapper.getValueOrEmptyAsString("app_name");
        String platform = wrapper.getValueOrEmptyAsString("platform");
        String os = wrapper.getValueOrEmptyAsString("os");
        String model = wrapper.getValueOrEmptyAsString("model");
        String locale = wrapper.getValueOrEmptyAsString("locale");
        String notificationChannel = wrapper.getValueOrEmptyAsString("notification_channel");
        String notificationToken = wrapper.getValueOrEmptyAsString("notification_token");
        JsonNodeWrapper availableAuthenticationMethodsNodes = wrapper.getNode("available_methods");
        List<String> availableAuthenticationMethods = availableAuthenticationMethodsNodes.toList();
        Integer priority = wrapper.getValueAsInteger("priority");
        AuthenticationDevice authenticationDevice =
            new AuthenticationDevice(
                id,
                appName,
                platform,
                os,
                model,
                locale,
                notificationChannel,
                notificationToken,
                availableAuthenticationMethods,
                priority);
        authenticationDevices.add(authenticationDevice);
      }
      user.setAuthenticationDevices(authenticationDevices);
    }

    if (stringMap.containsKey("verified_claims")
        && stringMap.get("verified_claims") != null
        && !stringMap.get("verified_claims").isEmpty()) {
      JsonNodeWrapper jsonNodeWrapper =
          JsonNodeWrapper.fromString(stringMap.get("verified_claims"));
      HashMap<String, Object> verifiedClaims = new HashMap<>(jsonNodeWrapper.toMap());
      user.setVerifiedClaims(verifiedClaims);
    }

    UserStatus userStatus = UserStatus.of(stringMap.getOrDefault("status", ""));
    user.setStatus(userStatus);

    return user;
  }

  static UserIdentifier extractUserIdentifier(Map<String, String> stringMap) {
    String id = stringMap.get("id");
    return new UserIdentifier(id);
  }
}
