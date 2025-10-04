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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.security.event.SecurityEventDetail;
import org.idp.server.platform.security.event.SecurityEventUser;
import org.idp.server.platform.security.event.SecurityEventUserAttributeConfiguration;
import org.idp.server.platform.security.log.SecurityEventLogConfiguration;

public interface SecurityEventUserCreatable {

  default SecurityEventUser createSecurityEventUser(User user) {

    String id = user.sub();
    String name = user.name();
    String externalUserId = user.externalUserId();
    String email = user.email();
    String phoneNumber = user.phoneNumber();

    String givenName = user.givenName();
    String familyName = user.familyName();
    String preferredUsername = user.preferredUsername();
    String profile = user.profile();
    String picture = user.picture();
    String website = user.website();
    String gender = user.gender();
    String birthdate = user.birthdate();
    String zoneinfo = user.zoneinfo();
    String locale = user.locale();

    List<String> roles = user.roles().stream().map(UserRole::roleName).collect(Collectors.toList());
    List<String> permissions = user.permissions();
    String currentTenant = user.currentTenant();
    List<String> assignedTenants = user.assignedTenants();

    return new SecurityEventUser(
        id,
        name,
        externalUserId,
        email,
        phoneNumber,
        givenName,
        familyName,
        preferredUsername,
        profile,
        picture,
        website,
        gender,
        birthdate,
        zoneinfo,
        locale,
        roles,
        permissions,
        currentTenant,
        assignedTenants);
  }

  default Map<String, Object> toDetailWithSensitiveData(User user, Tenant tenant) {
    SecurityEventUserAttributeConfiguration userConfig =
        SecurityEventUserAttributeConfiguration.fromTenantAttributes(tenant.attributes());

    Map<String, Object> result = new HashMap<>();

    // Always included safe fields
    if (userConfig.isIncludeId()) {
      result.put("sub", user.sub());
    }
    if (userConfig.isIncludeExternalUserId() && user.hasExternalUserId()) {
      result.put("ex_sub", user.externalUserId());
    }
    if (userConfig.isIncludeZoneinfo() && user.zoneinfo() != null) {
      result.put("zoneinfo", user.zoneinfo());
    }
    if (userConfig.isIncludeLocale() && user.locale() != null) {
      result.put("locale", user.locale());
    }
    if (userConfig.isIncludeRoles() && user.roles() != null && !user.roles().isEmpty()) {
      result.put(
          "roles", user.roles().stream().map(UserRole::roleName).collect(Collectors.toList()));
    }
    if (userConfig.isIncludePermissions()
        && user.permissions() != null
        && !user.permissions().isEmpty()) {
      result.put("permissions", user.permissions());
    }
    if (userConfig.isIncludeCurrentTenant() && user.currentTenant() != null) {
      result.put("current_tenant", user.currentTenant());
    }
    if (userConfig.isIncludeAssignedTenants()
        && user.assignedTenants() != null
        && !user.assignedTenants().isEmpty()) {
      result.put("assigned_tenants", user.assignedTenants());
    }

    if (userConfig.isIncludeName() && user.name() != null) {
      result.put("name", user.name());
    }
    if (userConfig.isIncludeEmail() && user.email() != null) {
      result.put("email", user.email());
    }
    if (userConfig.isIncludePhoneNumber() && user.phoneNumber() != null) {
      result.put("phone_number", user.phoneNumber());
    }
    if (userConfig.isIncludeGivenName() && user.givenName() != null) {
      result.put("given_name", user.givenName());
    }
    if (userConfig.isIncludeFamilyName() && user.familyName() != null) {
      result.put("family_name", user.familyName());
    }
    if (userConfig.isIncludePreferredUsername() && user.preferredUsername() != null) {
      result.put("preferred_username", user.preferredUsername());
    }
    if (userConfig.isIncludeProfile() && user.profile() != null) {
      result.put("profile", user.profile());
    }
    if (userConfig.isIncludePicture() && user.picture() != null) {
      result.put("picture", user.picture());
    }
    if (userConfig.isIncludeWebsite() && user.website() != null) {
      result.put("website", user.website());
    }
    if (userConfig.isIncludeGender() && user.gender() != null) {
      result.put("gender", user.gender());
    }
    if (userConfig.isIncludeBirthdate() && user.birthdate() != null) {
      result.put("birthdate", user.birthdate());
    }

    return result;
  }

  default SecurityEventDetail createSecurityEventDetailWithScrubbing(
      Map<String, Object> detailsMap, Tenant tenant) {
    SecurityEventLogConfiguration logConfig =
        new SecurityEventLogConfiguration(tenant.attributes());
    Map<String, Object> scrubbedDetails =
        scrubSensitiveDataInMap(detailsMap, logConfig.getDetailScrubKeys());
    return new SecurityEventDetail(scrubbedDetails);
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> scrubSensitiveDataInMap(
      Map<String, Object> data, List<String> scrubKeys) {
    Map<String, Object> scrubbedData = new HashMap<>();

    for (Map.Entry<String, Object> entry : data.entrySet()) {
      String key = entry.getKey();
      Object value = entry.getValue();

      if (shouldScrubKey(key, scrubKeys)) {
        scrubbedData.put(key, "[SCRUBBED]");
      } else if (value instanceof Map) {
        scrubbedData.put(key, scrubSensitiveDataInMap((Map<String, Object>) value, scrubKeys));
      } else if (value instanceof List) {
        scrubbedData.put(key, scrubSensitiveDataInList((List<Object>) value, scrubKeys));
      } else {
        scrubbedData.put(key, value);
      }
    }

    return scrubbedData;
  }

  @SuppressWarnings("unchecked")
  private List<Object> scrubSensitiveDataInList(List<Object> list, List<String> scrubKeys) {
    List<Object> scrubbedList = new ArrayList<>();

    for (Object item : list) {
      if (item instanceof Map) {
        scrubbedList.add(scrubSensitiveDataInMap((Map<String, Object>) item, scrubKeys));
      } else if (item instanceof List) {
        scrubbedList.add(scrubSensitiveDataInList((List<Object>) item, scrubKeys));
      } else {
        scrubbedList.add(item);
      }
    }

    return scrubbedList;
  }

  private boolean shouldScrubKey(String key, List<String> scrubKeys) {
    String lowerKey = key.toLowerCase().trim();
    return scrubKeys.stream()
        .map(s -> s.toLowerCase().trim())
        .anyMatch(scrubKey -> lowerKey.equals(scrubKey) || lowerKey.startsWith(scrubKey));
  }
}
