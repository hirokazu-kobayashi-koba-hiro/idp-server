package org.idp.server.oauth.identity;

import java.util.HashMap;
import java.util.Map;

public interface IndividualClaimsCreatable extends ClaimHashable {

  // FIXME user claim
  default Map<String, Object> createIndividualClaims(
      User user, IdTokenIndividualClaimsDecider claimsDecider) {

    HashMap<String, Object> claims = new HashMap<>();
    claims.put("sub", user.sub());
    if (claimsDecider.shouldAddName()) {
      claims.put("name", user.name());
    }
    if (claimsDecider.shouldAddGivenName()) {
      claims.put("given_name", user.givenName());
    }
    if (claimsDecider.shouldAddFamilyName()) {
      claims.put("family_name", user.familyName());
    }
    if (claimsDecider.shouldAddMiddleName()) {
      claims.put("middle_name", user.middleName());
    }
    if (claimsDecider.shouldAddNickname()) {
      claims.put("nickname", user.nickname());
    }
    if (claimsDecider.shouldAddPreferredUsername()) {
      claims.put("preferred_username", user.preferredUsername());
    }
    if (claimsDecider.shouldAddProfile()) {
      claims.put("profile", user.profile());
    }
    if (claimsDecider.shouldAddPicture()) {
      claims.put("picture", user.picture());
    }
    if (claimsDecider.shouldAddWebsite()) {
      claims.put("website", user.website());
    }
    if (claimsDecider.shouldAddEmail()) {
      claims.put("email", user.email());
    }
    if (claimsDecider.shouldAddEmailVerified()) {
      claims.put("email_verified", user.emailVerified());
    }
    if (claimsDecider.shouldAddGender()) {
      claims.put("gender", user.gender());
    }
    if (claimsDecider.shouldAddBirthdate()) {
      claims.put("birthdate", user.birthdate());
    }
    if (claimsDecider.shouldAddZoneinfo()) {
      claims.put("zoneinfo", user.zoneinfo());
    }
    if (claimsDecider.shouldAddLocale()) {
      claims.put("locale", user.locale());
    }
    if (claimsDecider.shouldAddPhoneNumber()) {
      claims.put("phone_number", user.phoneNumber());
    }
    if (claimsDecider.shouldAddPhoneNumberVerified()) {
      claims.put("phone_number_verified", user.phoneNumberVerified());
    }
    if (claimsDecider.shouldAddAddress()) {
      // TODO
    }
    if (claimsDecider.shouldAddUpdatedAt()) {
      claims.put("update_at", user.updateAt());
    }

    return claims;
  }

  default Map<String, Object> createIndividualClaims(
      User user, UserinfoIndividualClaimsDecider claimsDecider) {

    HashMap<String, Object> claims = new HashMap<>();
    claims.put("sub", user.sub());
    if (claimsDecider.shouldAddName()) {
      claims.put("name", user.name());
    }
    if (claimsDecider.shouldAddGivenName()) {
      claims.put("given_name", user.givenName());
    }
    if (claimsDecider.shouldAddMiddleName()) {
      claims.put("middle_name", user.middleName());
    }
    if (claimsDecider.shouldAddFamilyName()) {
      claims.put("family_name", user.familyName());
    }
    if (claimsDecider.shouldAddNickname()) {
      claims.put("nickname", user.nickname());
    }
    if (claimsDecider.shouldAddPreferredUsername()) {
      claims.put("preferred_username", user.preferredUsername());
    }
    if (claimsDecider.shouldAddProfile()) {
      claims.put("profile", user.profile());
    }
    if (claimsDecider.shouldAddPicture()) {
      claims.put("picture", user.picture());
    }
    if (claimsDecider.shouldAddWebsite()) {
      claims.put("website", user.website());
    }
    if (claimsDecider.shouldAddEmail()) {
      claims.put("email", user.email());
    }
    if (claimsDecider.shouldAddEmailVerified()) {
      claims.put("email_verified", user.emailVerified());
    }
    if (claimsDecider.shouldAddGender()) {
      claims.put("gender", user.gender());
    }
    if (claimsDecider.shouldAddBirthdate()) {
      claims.put("birthdate", user.birthdate());
    }
    if (claimsDecider.shouldAddZoneinfo()) {
      claims.put("zoneinfo", user.zoneinfo());
    }
    if (claimsDecider.shouldAddLocale()) {
      claims.put("locale", user.locale());
    }
    if (claimsDecider.shouldAddPhoneNumber()) {
      claims.put("phone_number", user.phoneNumber());
    }
    if (claimsDecider.shouldAddPhoneNumberVerified()) {
      claims.put("phone_number_verified", user.phoneNumberVerified());
    }
    if (claimsDecider.shouldAddAddress()) {
      // TODO
    }
    if (claimsDecider.shouldAddUpdatedAt()) {
      claims.put("update_at", user.updateAt());
    }

    return claims;
  }
}
