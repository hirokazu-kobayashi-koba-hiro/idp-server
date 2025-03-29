package org.idp.server.core.oauth.identity;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.oauth.grant.GrantIdTokenClaims;
import org.idp.server.core.oauth.grant.GrantUserinfoClaims;

public interface IndividualClaimsCreatable extends ClaimHashable {

  default Map<String, Object> createIndividualClaims(
      User user, GrantIdTokenClaims idTokenClaims, boolean idTokenStrictMode) {

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

    if (!idTokenStrictMode && user.hasRoles()) {
      claims.put("roles", user.roles());
    }

    if (!idTokenStrictMode && user.hasPermissions()) {
      claims.put("permissions", user.permissions());
    }

    if (!idTokenStrictMode && user.hasCustomProperties()) {
      claims.putAll(user.customPropertiesValue());
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

    if (user.hasRoles()) {
      claims.put("roles", user.roles());
    }

    if (user.hasPermissions()) {
      claims.put("permissions", user.permissions());
    }

    if (user.hasCustomProperties()) {
      claims.putAll(user.customPropertiesValue());
    }

    return claims;
  }
}
