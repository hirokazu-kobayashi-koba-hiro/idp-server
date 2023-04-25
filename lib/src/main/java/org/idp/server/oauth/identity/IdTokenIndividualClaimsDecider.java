package org.idp.server.oauth.identity;

import java.util.HashSet;
import java.util.Set;
import org.idp.server.type.oauth.Scopes;
import org.idp.server.type.oidc.Claims;

public class IdTokenIndividualClaimsDecider {
  Scopes scopes;
  IdTokenClaims idTokenClaims;
  boolean enabledStrictMode;

  public IdTokenIndividualClaimsDecider(
      Scopes scopes, IdTokenClaims idTokenClaims, boolean enabledStrictMode) {
    this.scopes = scopes;
    this.idTokenClaims = idTokenClaims;
    this.enabledStrictMode = enabledStrictMode;
  }

  public Claims decide() {
    Set<String> claimValues = new HashSet<>();
    if (shouldAddName()) {
      claimValues.add("name");
    }
    if (shouldAddGivenName()) {
      claimValues.add("given_name");
    }
    if (shouldAddMiddleName()) {
      claimValues.add("middle_name");
    }
    if (shouldAddNickname()) {
      claimValues.add("nickname");
    }
    if (shouldAddPreferredUsername()) {
      claimValues.add("preferred_username");
    }
    if (shouldAddProfile()) {
      claimValues.add("profile");
    }
    if (shouldAddPicture()) {
      claimValues.add("picture");
    }
    if (shouldAddWebsite()) {
      claimValues.add("website");
    }
    if (shouldAddEmail()) {
      claimValues.add("email");
    }
    if (shouldAddEmailVerified()) {
      claimValues.add("email_verified");
    }
    if (shouldAddGender()) {
      claimValues.add("gender");
    }
    if (shouldAddBirthdate()) {
      claimValues.add("birthdate");
    }
    if (shouldAddZoneinfo()) {
      claimValues.add("zoneinfo");
    }
    if (shouldAddLocale()) {
      claimValues.add("locale");
    }
    if (shouldAddPhoneNumber()) {
      claimValues.add("phone_number");
    }
    if (shouldAddPhoneNumberVerified()) {
      claimValues.add("phone_number_verified");
    }
    if (shouldAddAddress()) {
      // TODO
    }
    if (shouldAddUpdatedAt()) {
      claimValues.add("update_at");
    }
    return new Claims(claimValues);
  }

  public boolean shouldAddAuthTime() {
    return idTokenClaims.hasAuthTime();
  }

  public boolean shouldAddAcr() {
    return idTokenClaims.hasAcr();
  }

  public boolean shouldAddAmr() {
    return idTokenClaims.hasAmr();
  }

  public boolean shouldAddAzp() {
    return idTokenClaims.hasAzp();
  }

  public boolean shouldAddSub() {
    return true;
  }

  public boolean shouldAddName() {
    if (enabledStrictMode) {
      return idTokenClaims.hasName();
    }
    return scopes.contains("profile");
  }

  public boolean shouldAddGivenName() {
    if (enabledStrictMode) {
      return idTokenClaims.hasGivenName();
    }
    return scopes.contains("profile");
  }

  public boolean shouldAddMiddleName() {
    if (enabledStrictMode) {
      return idTokenClaims.hasMiddleName();
    }
    return scopes.contains("profile");
  }

  public boolean shouldAddNickname() {
    if (enabledStrictMode) {
      return idTokenClaims.hasNickname();
    }
    return scopes.contains("profile");
  }

  public boolean shouldAddPreferredUsername() {
    if (enabledStrictMode) {
      return idTokenClaims.hasPreferredUsername();
    }
    return scopes.contains("profile");
  }

  public boolean shouldAddProfile() {
    if (enabledStrictMode) {
      return idTokenClaims.hasProfile();
    }
    return scopes.contains("profile");
  }

  public boolean shouldAddPicture() {
    if (enabledStrictMode) {
      return idTokenClaims.hasPicture();
    }
    return scopes.contains("profile");
  }

  public boolean shouldAddWebsite() {
    if (enabledStrictMode) {
      return idTokenClaims.hasProfile();
    }
    return scopes.contains("profile");
  }

  public boolean shouldAddEmail() {
    if (enabledStrictMode) {
      return idTokenClaims.hasEmail();
    }
    return scopes.contains("email");
  }

  public boolean shouldAddEmailVerified() {
    if (enabledStrictMode) {
      return idTokenClaims.hasEmailVerified();
    }
    return scopes.contains("email");
  }

  public boolean shouldAddGender() {
    if (enabledStrictMode) {
      return idTokenClaims.hasGender();
    }
    return scopes.contains("profile");
  }

  public boolean shouldAddBirthdate() {
    if (enabledStrictMode) {
      return idTokenClaims.hasBirthdate();
    }
    return scopes.contains("profile");
  }

  public boolean shouldAddZoneinfo() {
    if (enabledStrictMode) {
      return idTokenClaims.hasZoneinfo();
    }
    return scopes.contains("profile");
  }

  public boolean shouldAddLocale() {
    if (enabledStrictMode) {
      return idTokenClaims.hasLocale();
    }
    return scopes.contains("profile");
  }

  public boolean shouldAddPhoneNumber() {
    if (enabledStrictMode) {
      return idTokenClaims.hasName();
    }
    return scopes.contains("phone");
  }

  public boolean shouldAddPhoneNumberVerified() {
    if (enabledStrictMode) {
      return idTokenClaims.hasPhoneNumberVerified();
    }
    return scopes.contains("phone");
  }

  public boolean shouldAddAddress() {
    if (enabledStrictMode) {
      return idTokenClaims.hasAddress();
    }
    return scopes.contains("address");
  }

  public boolean shouldAddUpdatedAt() {
    if (enabledStrictMode) {
      return idTokenClaims.hasUpdatedAt();
    }
    return scopes.contains("profile");
  }
}
