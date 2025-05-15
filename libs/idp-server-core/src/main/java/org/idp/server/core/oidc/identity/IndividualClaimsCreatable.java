package org.idp.server.core.oidc.identity;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.basic.json.JsonNodeWrapper;
import org.idp.server.core.identity.User;
import org.idp.server.core.oidc.grant.GrantIdTokenClaims;
import org.idp.server.core.oidc.grant.GrantUserinfoClaims;

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

    if (!idTokenStrictMode && user.hasRoles()) {
      claims.put("roles", user.roleNameAsListString());
    }

    if (!idTokenStrictMode && user.hasPermissions()) {
      claims.put("permissions", user.permissions());
    }

    if (!idTokenStrictMode && user.hasAssignedTenants()) {
      claims.put("assigned_tenants", user.assignedTenants());
    }

    if (!idTokenStrictMode && user.hasCustomProperties()) {
      claims.putAll(user.customPropertiesValue());
    }

    if (idTokenClaims.hasVerifiedClaims() && user.hasVerifiedClaims()) {
      VerifiedClaimsObject verifiedClaimsObject = requestedIdTokenClaims.verifiedClaims();
      JsonNodeWrapper userVerifiedClaims = user.verifiedClaimsNodeWrapper();
      Map<String, Object> verified = new HashMap<>();

      JsonNodeWrapper verificationNodeWrapper = verifiedClaimsObject.verificationNodeWrapper();
      Map<String, Object> verification = new HashMap<>();
      JsonNodeWrapper verificationClaim = userVerifiedClaims.getValueAsJsonNode("verification");
      if (verificationNodeWrapper.contains("trust_framework")
          && verificationClaim.contains("trust_framework")) {
        verification.put(
            "trust_framework", verificationClaim.getValueOrEmptyAsString("trust_framework"));
      }
      if (verificationNodeWrapper.contains("evidence") && verificationClaim.contains("evidence")) {
        verification.put("evidence", verificationClaim.getValueAsJsonNodeListAsMap("evidence"));
      }

      JsonNodeWrapper claimsNodeWrapper = verifiedClaimsObject.claimsNodeWrapper();
      JsonNodeWrapper userClaims = userVerifiedClaims.getValueAsJsonNode("claims");
      Map<String, Object> verifiedClaims = new HashMap<>();
      if (claimsNodeWrapper.contains("name") && userClaims.contains("name")) {
        verifiedClaims.put("name", userClaims.getValueOrEmptyAsString("name"));
      }
      if (claimsNodeWrapper.contains("given_name") && userClaims.contains("given_name")) {
        verifiedClaims.put("given_name", userClaims.getValueOrEmptyAsString("given_name"));
      }
      if (claimsNodeWrapper.contains("family_name") && userClaims.contains("family_name")) {
        verifiedClaims.put("family_name", userClaims.getValueOrEmptyAsString("family_name"));
      }
      if (claimsNodeWrapper.contains("middle_name") && userClaims.contains("middle_name")) {
        verifiedClaims.put("middle_name", userClaims.getValueOrEmptyAsString("middle_name"));
      }
      if (claimsNodeWrapper.contains("gender") && userClaims.contains("gender")) {
        verifiedClaims.put("gender", userClaims.getValueOrEmptyAsString("gender"));
      }
      if (claimsNodeWrapper.contains("birthdate") && userClaims.contains("birthdate")) {
        verifiedClaims.put("birthdate", userClaims.getValueOrEmptyAsString("birthdate"));
      }
      if (claimsNodeWrapper.contains("locale") && userClaims.contains("locale")) {
        verifiedClaims.put("locale", userClaims.getValueOrEmptyAsString("locale"));
      }
      if (claimsNodeWrapper.contains("address") && userClaims.contains("address")) {
        verifiedClaims.put("address", userClaims.getValueAsJsonNode("address").toMap());
      }
      if (claimsNodeWrapper.contains("phone_number") && userClaims.contains("phone_number")) {
        verifiedClaims.put("phone_number", userClaims.getValueOrEmptyAsString("phone_number"));
      }
      if (claimsNodeWrapper.contains("phone_number_verified")
          && userClaims.contains("phone_number_verified")) {
        verifiedClaims.put(
            "phone_number_verified", userClaims.getValueAsBoolean("phone_number_verified"));
      }
      if (claimsNodeWrapper.contains("email") && userClaims.contains("email")) {
        verifiedClaims.put("email", userClaims.getValueOrEmptyAsString("email"));
      }
      if (claimsNodeWrapper.contains("email_verified") && userClaims.contains("email_verified")) {
        verifiedClaims.put(
            "email_verified", userVerifiedClaims.getValueAsBoolean("email_verified"));
      }
      verified.put("verification", verification);
      verified.put("claims", verifiedClaims);
      claims.put("verified_claims", verified);
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
      claims.put("roles", user.roleNameAsListString());
    }

    if (user.hasPermissions()) {
      claims.put("permissions", user.permissions());
    }

    if (user.hasAssignedTenants()) {
      claims.put("assigned_tenants", user.assignedTenants());
    }

    if (user.hasCustomProperties()) {
      claims.putAll(user.customPropertiesValue());
    }

    return claims;
  }
}
