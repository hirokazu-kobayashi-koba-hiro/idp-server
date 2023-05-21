package org.idp.server.oauth.identity;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.idp.server.type.oauth.Scopes;
import org.idp.server.type.oidc.Claims;

public class UserinfoIndividualClaimsDecider {
  Scopes scopes;
  UserinfoClaims userinfoClaims;
  List<String> supportedClaims;

  public UserinfoIndividualClaimsDecider(
      Scopes scopes, UserinfoClaims userinfoClaims, List<String> supportedClaims) {
    this.scopes = scopes;
    this.userinfoClaims = userinfoClaims;
    this.supportedClaims = supportedClaims;
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

  public boolean shouldAddSub() {
    return true;
  }

  public boolean shouldAddName() {
    if (!supportedClaims.contains("name")) {
      return false;
    }
    return scopes.contains("profile") || userinfoClaims.hasName();
  }

  public boolean shouldAddGivenName() {
    if (!supportedClaims.contains("given_name")) {
      return false;
    }
    return scopes.contains("profile") || userinfoClaims.hasGivenName();
  }

  public boolean shouldAddFamilyName() {
    if (!supportedClaims.contains("family_name")) {
      return false;
    }
    return scopes.contains("profile") || userinfoClaims.hasMiddleName();
  }

  public boolean shouldAddMiddleName() {
    if (!supportedClaims.contains("middle_name")) {
      return false;
    }
    return scopes.contains("profile") || userinfoClaims.hasMiddleName();
  }

  public boolean shouldAddNickname() {
    if (!supportedClaims.contains("nickname")) {
      return false;
    }
    return scopes.contains("profile") || userinfoClaims.hasNickname();
  }

  public boolean shouldAddPreferredUsername() {
    if (!supportedClaims.contains("preferred_username")) {
      return false;
    }
    return scopes.contains("profile") || userinfoClaims.hasPreferredUsername();
  }

  public boolean shouldAddProfile() {
    if (!supportedClaims.contains("profile")) {
      return false;
    }
    return scopes.contains("profile") || userinfoClaims.hasProfile();
  }

  public boolean shouldAddPicture() {
    if (!supportedClaims.contains("picture")) {
      return false;
    }
    return scopes.contains("profile") || userinfoClaims.hasPicture();
  }

  public boolean shouldAddWebsite() {
    if (!supportedClaims.contains("website")) {
      return false;
    }
    return scopes.contains("profile") || userinfoClaims.hasWebsite();
  }

  public boolean shouldAddEmail() {
    if (!supportedClaims.contains("email")) {
      return false;
    }
    return scopes.contains("email") || userinfoClaims.hasEmail();
  }

  public boolean shouldAddEmailVerified() {
    if (!supportedClaims.contains("email_verified")) {
      return false;
    }
    return scopes.contains("email") || userinfoClaims.hasEmailVerified();
  }

  public boolean shouldAddGender() {
    if (!supportedClaims.contains("gender")) {
      return false;
    }
    return scopes.contains("profile") || userinfoClaims.hasGender();
  }

  public boolean shouldAddBirthdate() {
    if (!supportedClaims.contains("birthdate")) {
      return false;
    }
    return scopes.contains("profile") || userinfoClaims.hasBirthdate();
  }

  public boolean shouldAddZoneinfo() {
    if (!supportedClaims.contains("zoneinfo")) {
      return false;
    }
    return scopes.contains("profile") || userinfoClaims.hasZoneinfo();
  }

  public boolean shouldAddLocale() {
    if (!supportedClaims.contains("locale")) {
      return false;
    }
    return scopes.contains("profile") || userinfoClaims.hasLocale();
  }

  public boolean shouldAddPhoneNumber() {
    if (!supportedClaims.contains("phone_number")) {
      return false;
    }
    return scopes.contains("phone") || userinfoClaims.hasPhoneNumber();
  }

  public boolean shouldAddPhoneNumberVerified() {
    if (!supportedClaims.contains("phone_number_verified")) {
      return false;
    }
    return scopes.contains("phone") || userinfoClaims.hasPhoneNumberVerified();
  }

  public boolean shouldAddAddress() {
    if (!supportedClaims.contains("address")) {
      return false;
    }
    return scopes.contains("address") || userinfoClaims.hasAddress();
  }

  public boolean shouldAddUpdatedAt() {
    if (!supportedClaims.contains("updated_at")) {
      return false;
    }
    return scopes.contains("profile") || userinfoClaims.hasUpdatedAt();
  }
}
