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

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.security.event.SecurityEventUser;
import org.idp.server.platform.security.event.SecurityEventUserAttributeConfiguration;

public interface SecurityEventUserCreatable {

  default SecurityEventUser createSecurityEventUser(User user, Tenant tenant) {
    SecurityEventUserAttributeConfiguration config =
        tenant.getSecurityEventUserAttributeConfiguration();

    String id = config.isIncludeId() ? user.sub() : null;
    String name = config.isIncludeName() ? user.name() : null;
    String externalUserId = config.isIncludeExternalUserId() ? user.externalUserId() : null;
    String email = config.isIncludeEmail() ? user.email() : null;
    String phoneNumber = config.isIncludePhoneNumber() ? user.phoneNumber() : null;

    String givenName = config.isIncludeGivenName() ? user.givenName() : null;
    String familyName = config.isIncludeFamilyName() ? user.familyName() : null;
    String preferredUsername =
        config.isIncludePreferredUsername() ? user.preferredUsername() : null;
    String profile = config.isIncludeProfile() ? user.profile() : null;
    String picture = config.isIncludePicture() ? user.picture() : null;
    String website = config.isIncludeWebsite() ? user.website() : null;
    String gender = config.isIncludeGender() ? user.gender() : null;
    String birthdate = config.isIncludeBirthdate() ? user.birthdate() : null;
    String zoneinfo = config.isIncludeZoneinfo() ? user.zoneinfo() : null;
    String locale = config.isIncludeLocale() ? user.locale() : null;

    Map<String, Object> address =
        config.isIncludeAddress() && user.address() != null ? user.address().toMap() : null;
    List<String> roles =
        config.isIncludeRoles()
            ? user.roles().stream().map(role -> role.roleName()).collect(Collectors.toList())
            : null;
    List<String> permissions = config.isIncludePermissions() ? user.permissions() : null;
    String currentTenant = config.isIncludeCurrentTenant() ? user.currentTenant() : null;
    List<String> assignedTenants =
        config.isIncludeAssignedTenants() ? user.assignedTenants() : null;
    Map<String, Object> verifiedClaims =
        config.isIncludeVerifiedClaims() ? user.verifiedClaims() : null;

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
        address,
        roles,
        permissions,
        currentTenant,
        assignedTenants,
        verifiedClaims);
  }
}
