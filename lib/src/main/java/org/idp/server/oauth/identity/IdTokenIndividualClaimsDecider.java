package org.idp.server.oauth.identity;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.idp.server.type.oauth.Scopes;
import org.idp.server.type.oidc.Claims;

public class IdTokenIndividualClaimsDecider {
  Scopes scopes;
  IdTokenClaims idTokenClaims;
  List<String> supportedClaims;
  boolean enabledStrictMode;

  public IdTokenIndividualClaimsDecider(
      Scopes scopes,
      IdTokenClaims idTokenClaims,
      List<String> supportedClaims,
      boolean enabledStrictMode) {
    this.scopes = scopes;
    this.idTokenClaims = idTokenClaims;
    this.supportedClaims = supportedClaims;
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
    if (shouldAddFamilyName()) {
      claimValues.add("family_name");
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
    if (!supportedClaims.contains("name")) {
      return false;
    }
    if (enabledStrictMode) {
      return idTokenClaims.hasName();
    }
    return scopes.contains("profile");
  }

  public boolean shouldAddGivenName() {
    if (!supportedClaims.contains("given_name")) {
      return false;
    }
    if (enabledStrictMode) {
      return idTokenClaims.hasGivenName();
    }
    return scopes.contains("profile");
  }

  public boolean shouldAddFamilyName() {
    if (!supportedClaims.contains("family_name")) {
      return false;
    }
    if (enabledStrictMode) {
      return idTokenClaims.hasGivenName();
    }
    return scopes.contains("profile");
  }

  public boolean shouldAddMiddleName() {
    if (!supportedClaims.contains("middle_name")) {
      return false;
    }
    if (enabledStrictMode) {
      return idTokenClaims.hasMiddleName();
    }
    return scopes.contains("profile");
  }

  public boolean shouldAddNickname() {
    if (!supportedClaims.contains("nickname")) {
      return false;
    }
    if (enabledStrictMode) {
      return idTokenClaims.hasNickname();
    }
    return scopes.contains("profile");
  }

  public boolean shouldAddPreferredUsername() {
    if (!supportedClaims.contains("preferred_username")) {
      return false;
    }
    if (enabledStrictMode) {
      return idTokenClaims.hasPreferredUsername();
    }
    return scopes.contains("profile");
  }

  public boolean shouldAddProfile() {
    if (!supportedClaims.contains("profile")) {
      return false;
    }
    if (enabledStrictMode) {
      return idTokenClaims.hasProfile();
    }
    return scopes.contains("profile");
  }

  public boolean shouldAddPicture() {
    if (!supportedClaims.contains("picture")) {
      return false;
    }
    if (enabledStrictMode) {
      return idTokenClaims.hasPicture();
    }
    return scopes.contains("profile");
  }

  public boolean shouldAddWebsite() {
    if (!supportedClaims.contains("website")) {
      return false;
    }
    if (enabledStrictMode) {
      return idTokenClaims.hasProfile();
    }
    return scopes.contains("profile");
  }

  public boolean shouldAddEmail() {
    if (!supportedClaims.contains("email")) {
      return false;
    }
    if (enabledStrictMode) {
      return idTokenClaims.hasEmail();
    }
    return scopes.contains("email");
  }

  public boolean shouldAddEmailVerified() {
    if (!supportedClaims.contains("email_verified")) {
      return false;
    }
    if (enabledStrictMode) {
      return idTokenClaims.hasEmailVerified();
    }
    return scopes.contains("email");
  }

  public boolean shouldAddGender() {
    if (!supportedClaims.contains("gender")) {
      return false;
    }
    if (enabledStrictMode) {
      return idTokenClaims.hasGender();
    }
    return scopes.contains("profile");
  }

  public boolean shouldAddBirthdate() {
    if (!supportedClaims.contains("birthdate")) {
      return false;
    }
    if (enabledStrictMode) {
      return idTokenClaims.hasBirthdate();
    }
    return scopes.contains("profile");
  }

  public boolean shouldAddZoneinfo() {
    if (!supportedClaims.contains("zoneinfo")) {
      return false;
    }
    if (enabledStrictMode) {
      return idTokenClaims.hasZoneinfo();
    }
    return scopes.contains("profile");
  }

  public boolean shouldAddLocale() {
    if (!supportedClaims.contains("locale")) {
      return false;
    }
    if (enabledStrictMode) {
      return idTokenClaims.hasLocale();
    }
    return scopes.contains("profile");
  }

  public boolean shouldAddPhoneNumber() {
    if (!supportedClaims.contains("phone_number")) {
      return false;
    }
    if (enabledStrictMode) {
      return idTokenClaims.hasName();
    }
    return scopes.contains("phone");
  }

  public boolean shouldAddPhoneNumberVerified() {
    if (!supportedClaims.contains("phone_number_verified")) {
      return false;
    }
    if (enabledStrictMode) {
      return idTokenClaims.hasPhoneNumberVerified();
    }
    return scopes.contains("phone");
  }

  public boolean shouldAddAddress() {
    if (!supportedClaims.contains("address")) {
      return false;
    }
    if (enabledStrictMode) {
      return idTokenClaims.hasAddress();
    }
    return scopes.contains("address");
  }

  public boolean shouldAddUpdatedAt() {
    if (!supportedClaims.contains("updated_at")) {
      return false;
    }
    if (enabledStrictMode) {
      return idTokenClaims.hasUpdatedAt();
    }
    return scopes.contains("profile");
  }
}
