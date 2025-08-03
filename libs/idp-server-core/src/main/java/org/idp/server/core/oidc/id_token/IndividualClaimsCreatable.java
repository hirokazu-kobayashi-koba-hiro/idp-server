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

package org.idp.server.core.oidc.id_token;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.oidc.grant.GrantIdTokenClaims;
import org.idp.server.core.oidc.grant.GrantUserinfoClaims;
import org.idp.server.core.oidc.identity.User;

public interface IndividualClaimsCreatable extends ClaimHashable {

  default Map<String, Object> createIndividualClaims(
      User user,
      GrantIdTokenClaims idTokenClaims,
      boolean idTokenStrictMode,
      RequestedIdTokenClaims requestedIdTokenClaims) {

    HashMap<String, Object> claims = new HashMap<>();

    claims.put("sub", user.sub());
    if (idTokenClaims.hasName() && user.hasName()) {
      claims.put("name", user.name());
    }
    if (idTokenClaims.hasGivenName() && user.hasGivenName()) {
      claims.put("given_name", user.givenName());
    }
    if (idTokenClaims.hasFamilyName() && user.hasFamilyName()) {
      claims.put("family_name", user.familyName());
    }
    if (idTokenClaims.hasMiddleName() && user.hasMiddleName()) {
      claims.put("middle_name", user.middleName());
    }
    if (idTokenClaims.hasNickname() && user.hasNickname()) {
      claims.put("nickname", user.nickname());
    }
    if (idTokenClaims.hasPreferredUsername() && user.hasPreferredUsername()) {
      claims.put("preferred_username", user.preferredUsername());
    }
    if (idTokenClaims.hasProfile() && user.hasProfile()) {
      claims.put("profile", user.profile());
    }
    if (idTokenClaims.hasPicture() && user.hasPicture()) {
      claims.put("picture", user.picture());
    }
    if (idTokenClaims.hasWebsite() && user.hasWebsite()) {
      claims.put("website", user.website());
    }
    if (idTokenClaims.hasEmail() && user.hasEmail()) {
      claims.put("email", user.email());
    }
    if (idTokenClaims.hasEmailVerified() && user.hasEmailVerified()) {
      claims.put("email_verified", user.emailVerified());
    }
    if (idTokenClaims.hasGender() && user.hasGender()) {
      claims.put("gender", user.gender());
    }
    if (idTokenClaims.hasBirthdate() && user.hasBirthdate()) {
      claims.put("birthdate", user.birthdate());
    }
    if (idTokenClaims.hasZoneinfo() && user.hasZoneinfo()) {
      claims.put("zoneinfo", user.zoneinfo());
    }
    if (idTokenClaims.hasLocale() && user.hasLocale()) {
      claims.put("locale", user.locale());
    }
    if (idTokenClaims.hasPhoneNumber() && user.hasPhoneNumber()) {
      claims.put("phone_number", user.phoneNumber());
    }
    if (idTokenClaims.hasPhoneNumberVerified() && user.hasPhoneNumberVerified()) {
      claims.put("phone_number_verified", user.phoneNumberVerified());
    }
    if (idTokenClaims.hasAddress() && user.hasAddress()) {
      claims.put("address", user.address().toMap());
    }
    if (idTokenClaims.hasUpdatedAt() && user.hasUpdatedAt()) {
      claims.put("updated_at", user.updateAtAsLong());
    }

    return claims;
  }

  default Map<String, Object> createIndividualClaims(
      User user, GrantUserinfoClaims userinfoClaims) {

    HashMap<String, Object> claims = new HashMap<>();

    claims.put("sub", user.sub());
    if (userinfoClaims.hasName() && user.hasName()) {
      claims.put("name", user.name());
    }
    if (userinfoClaims.hasGivenName() && user.hasGivenName()) {
      claims.put("given_name", user.givenName());
    }
    if (userinfoClaims.hasFamilyName() && user.hasFamilyName()) {
      claims.put("family_name", user.familyName());
    }
    if (userinfoClaims.hasMiddleName() && user.hasMiddleName()) {
      claims.put("middle_name", user.middleName());
    }
    if (userinfoClaims.hasNickname() && user.hasNickname()) {
      claims.put("nickname", user.nickname());
    }
    if (userinfoClaims.hasPreferredUsername() && user.hasPreferredUsername()) {
      claims.put("preferred_username", user.preferredUsername());
    }
    if (userinfoClaims.hasProfile() && user.hasProfile()) {
      claims.put("profile", user.profile());
    }
    if (userinfoClaims.hasPicture() && user.hasPicture()) {
      claims.put("picture", user.picture());
    }
    if (userinfoClaims.hasWebsite() && user.hasWebsite()) {
      claims.put("website", user.website());
    }
    if (userinfoClaims.hasEmail() && user.hasEmail()) {
      claims.put("email", user.email());
    }
    if (userinfoClaims.hasEmailVerified() && user.hasEmailVerified()) {
      claims.put("email_verified", user.emailVerified());
    }
    if (userinfoClaims.hasGender() && user.hasGender()) {
      claims.put("gender", user.gender());
    }
    if (userinfoClaims.hasBirthdate() && user.hasBirthdate()) {
      claims.put("birthdate", user.birthdate());
    }
    if (userinfoClaims.hasZoneinfo() && user.hasZoneinfo()) {
      claims.put("zoneinfo", user.zoneinfo());
    }
    if (userinfoClaims.hasLocale() && user.hasLocale()) {
      claims.put("locale", user.locale());
    }
    if (userinfoClaims.hasPhoneNumber() && user.hasPhoneNumber()) {
      claims.put("phone_number", user.phoneNumber());
    }
    if (userinfoClaims.hasPhoneNumberVerified() && user.hasPhoneNumberVerified()) {
      claims.put("phone_number_verified", user.phoneNumberVerified());
    }
    if (userinfoClaims.hasAddress() && user.hasAddress()) {
      claims.put("address", user.address().toMap());
    }
    if (userinfoClaims.hasUpdatedAt() && user.hasUpdatedAt()) {
      claims.put("updated_at", user.updateAtAsLong());
    }

    // TODO ↓ move plugin
    if (user.hasExternalUserId()) {
      claims.put("ex_sub", user.externalUserId());
    }

    if (user.hasRoles()) {
      claims.put("roles", user.roleNameAsListString());
    }

    if (user.hasPermissions()) {
      claims.put("permissions", user.permissions());
    }

    if (user.hasAssignedTenants()) {
      claims.put("assigned_tenants", user.assignedTenants());
    }

    if (user.hasAuthenticationDevices()) {
      claims.put("authentication_devices", user.authenticationDevices().toMapList());
    }

    if (user.hasCustomProperties()) {
      claims.putAll(user.customPropertiesValue());
    }

    return claims;
  }
}
