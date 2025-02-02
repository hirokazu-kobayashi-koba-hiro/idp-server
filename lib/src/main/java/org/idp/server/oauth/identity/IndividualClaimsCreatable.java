package org.idp.server.oauth.identity;

import java.util.HashMap;
import java.util.Map;

public interface IndividualClaimsCreatable extends ClaimHashable {

  default Map<String, Object> createIndividualClaims(
      User user, IdTokenIndividualClaimsDecider claimsDecider) {

    HashMap<String, Object> claims = new HashMap<>();
    claims.put("sub", user.sub());
    if (claimsDecider.shouldAddName() && user.hasName()) {
      claims.put("name", user.name());
    }
    if (claimsDecider.shouldAddGivenName() && user.hasGivenName()) {
      claims.put("given_name", user.givenName());
    }
    if (claimsDecider.shouldAddFamilyName() && user.hasFamilyName()) {
      claims.put("family_name", user.familyName());
    }
    if (claimsDecider.shouldAddMiddleName() && user.hasMiddleName()) {
      claims.put("middle_name", user.middleName());
    }
    if (claimsDecider.shouldAddNickname() && user.hasNickname()) {
      claims.put("nickname", user.nickname());
    }
    if (claimsDecider.shouldAddPreferredUsername() && user.hasPreferredUsername()) {
      claims.put("preferred_username", user.preferredUsername());
    }
    if (claimsDecider.shouldAddProfile() && user.hasProfile()) {
      claims.put("profile", user.profile());
    }
    if (claimsDecider.shouldAddPicture() && user.hasPicture()) {
      claims.put("picture", user.picture());
    }
    if (claimsDecider.shouldAddWebsite() && user.hasWebsite()) {
      claims.put("website", user.website());
    }
    if (claimsDecider.shouldAddEmail() && user.hasEmail()) {
      claims.put("email", user.email());
    }
    if (claimsDecider.shouldAddEmailVerified() && user.hasEmailVerified()) {
      claims.put("email_verified", user.emailVerified());
    }
    if (claimsDecider.shouldAddGender() && user.hasGender()) {
      claims.put("gender", user.gender());
    }
    if (claimsDecider.shouldAddBirthdate() && user.hasBirthdate()) {
      claims.put("birthdate", user.birthdate());
    }
    if (claimsDecider.shouldAddZoneinfo() && user.hasZoneinfo()) {
      claims.put("zoneinfo", user.zoneinfo());
    }
    if (claimsDecider.shouldAddLocale() && user.hasLocale()) {
      claims.put("locale", user.locale());
    }
    if (claimsDecider.shouldAddPhoneNumber() && user.hasPhoneNumber()) {
      claims.put("phone_number", user.phoneNumber());
    }
    if (claimsDecider.shouldAddPhoneNumberVerified() && user.hasPhoneNumberVerified()) {
      claims.put("phone_number_verified", user.phoneNumberVerified());
    }
    if (claimsDecider.shouldAddAddress() && user.hasAddress()) {
      claims.put("address", user.address().values());
    }
    if (claimsDecider.shouldAddUpdatedAt() && user.hasUpdatedAt()) {
      claims.put("updated_at", user.updateAtAsLong());
    }

    return claims;
  }

  default Map<String, Object> createIndividualClaims(
      User user, UserinfoIndividualClaimsDecider claimsDecider) {

    HashMap<String, Object> claims = new HashMap<>();
    claims.put("sub", user.sub());
    if (claimsDecider.shouldAddName() && user.hasName()) {
      claims.put("name", user.name());
    }
    if (claimsDecider.shouldAddGivenName() && user.hasGivenName()) {
      claims.put("given_name", user.givenName());
    }
    if (claimsDecider.shouldAddFamilyName() && user.hasFamilyName()) {
      claims.put("family_name", user.familyName());
    }
    if (claimsDecider.shouldAddMiddleName() && user.hasMiddleName()) {
      claims.put("middle_name", user.middleName());
    }
    if (claimsDecider.shouldAddNickname() && user.hasNickname()) {
      claims.put("nickname", user.nickname());
    }
    if (claimsDecider.shouldAddPreferredUsername() && user.hasPreferredUsername()) {
      claims.put("preferred_username", user.preferredUsername());
    }
    if (claimsDecider.shouldAddProfile() && user.hasProfile()) {
      claims.put("profile", user.profile());
    }
    if (claimsDecider.shouldAddPicture() && user.hasPicture()) {
      claims.put("picture", user.picture());
    }
    if (claimsDecider.shouldAddWebsite() && user.hasWebsite()) {
      claims.put("website", user.website());
    }
    if (claimsDecider.shouldAddEmail() && user.hasEmail()) {
      claims.put("email", user.email());
    }
    if (claimsDecider.shouldAddEmailVerified() && user.hasEmailVerified()) {
      claims.put("email_verified", user.emailVerified());
    }
    if (claimsDecider.shouldAddGender() && user.hasGender()) {
      claims.put("gender", user.gender());
    }
    if (claimsDecider.shouldAddBirthdate() && user.hasBirthdate()) {
      claims.put("birthdate", user.birthdate());
    }
    if (claimsDecider.shouldAddZoneinfo() && user.hasZoneinfo()) {
      claims.put("zoneinfo", user.zoneinfo());
    }
    if (claimsDecider.shouldAddLocale() && user.hasLocale()) {
      claims.put("locale", user.locale());
    }
    if (claimsDecider.shouldAddPhoneNumber() && user.hasPhoneNumber()) {
      claims.put("phone_number", user.phoneNumber());
    }
    if (claimsDecider.shouldAddPhoneNumberVerified() && user.hasPhoneNumberVerified()) {
      claims.put("phone_number_verified", user.phoneNumberVerified());
    }
    if (claimsDecider.shouldAddAddress() && user.hasAddress()) {
      claims.put("address", user.address().values());
    }
    if (claimsDecider.shouldAddUpdatedAt() && user.hasUpdatedAt()) {
      claims.put("updated_at", user.updateAtAsLong());
    }

    return claims;
  }
}
