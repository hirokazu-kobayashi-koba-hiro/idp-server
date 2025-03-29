package org.idp.server.core.oauth.identity;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.idp.server.core.type.oauth.Scopes;
import org.idp.server.core.type.oidc.Claims;

public class UserinfoIndividualClaimsDecider {
  Scopes scopes;
  RequestedUserinfoClaims requestedUserinfoClaims;
  List<String> supportedClaims;

  public UserinfoIndividualClaimsDecider(
      Scopes scopes,
      RequestedUserinfoClaims requestedUserinfoClaims,
      List<String> supportedClaims) {
    this.scopes = scopes;
    this.requestedUserinfoClaims = requestedUserinfoClaims;
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
    return scopes.contains("profile") || requestedUserinfoClaims.hasName();
  }

  public boolean shouldAddGivenName() {
    if (!supportedClaims.contains("given_name")) {
      return false;
    }
    return scopes.contains("profile") || requestedUserinfoClaims.hasGivenName();
  }

  public boolean shouldAddFamilyName() {
    if (!supportedClaims.contains("family_name")) {
      return false;
    }
    return scopes.contains("profile") || requestedUserinfoClaims.hasMiddleName();
  }

  public boolean shouldAddMiddleName() {
    if (!supportedClaims.contains("middle_name")) {
      return false;
    }
    return scopes.contains("profile") || requestedUserinfoClaims.hasMiddleName();
  }

  public boolean shouldAddNickname() {
    if (!supportedClaims.contains("nickname")) {
      return false;
    }
    return scopes.contains("profile") || requestedUserinfoClaims.hasNickname();
  }

  public boolean shouldAddPreferredUsername() {
    if (!supportedClaims.contains("preferred_username")) {
      return false;
    }
    return scopes.contains("profile") || requestedUserinfoClaims.hasPreferredUsername();
  }

  public boolean shouldAddProfile() {
    if (!supportedClaims.contains("profile")) {
      return false;
    }
    return scopes.contains("profile") || requestedUserinfoClaims.hasProfile();
  }

  public boolean shouldAddPicture() {
    if (!supportedClaims.contains("picture")) {
      return false;
    }
    return scopes.contains("profile") || requestedUserinfoClaims.hasPicture();
  }

  public boolean shouldAddWebsite() {
    if (!supportedClaims.contains("website")) {
      return false;
    }
    return scopes.contains("profile") || requestedUserinfoClaims.hasWebsite();
  }

  public boolean shouldAddEmail() {
    if (!supportedClaims.contains("email")) {
      return false;
    }
    return scopes.contains("email") || requestedUserinfoClaims.hasEmail();
  }

  public boolean shouldAddEmailVerified() {
    if (!supportedClaims.contains("email_verified")) {
      return false;
    }
    return scopes.contains("email") || requestedUserinfoClaims.hasEmailVerified();
  }

  public boolean shouldAddGender() {
    if (!supportedClaims.contains("gender")) {
      return false;
    }
    return scopes.contains("profile") || requestedUserinfoClaims.hasGender();
  }

  public boolean shouldAddBirthdate() {
    if (!supportedClaims.contains("birthdate")) {
      return false;
    }
    return scopes.contains("profile") || requestedUserinfoClaims.hasBirthdate();
  }

  public boolean shouldAddZoneinfo() {
    if (!supportedClaims.contains("zoneinfo")) {
      return false;
    }
    return scopes.contains("profile") || requestedUserinfoClaims.hasZoneinfo();
  }

  public boolean shouldAddLocale() {
    if (!supportedClaims.contains("locale")) {
      return false;
    }
    return scopes.contains("profile") || requestedUserinfoClaims.hasLocale();
  }

  public boolean shouldAddPhoneNumber() {
    if (!supportedClaims.contains("phone_number")) {
      return false;
    }
    return scopes.contains("phone") || requestedUserinfoClaims.hasPhoneNumber();
  }

  public boolean shouldAddPhoneNumberVerified() {
    if (!supportedClaims.contains("phone_number_verified")) {
      return false;
    }
    return scopes.contains("phone") || requestedUserinfoClaims.hasPhoneNumberVerified();
  }

  public boolean shouldAddAddress() {
    if (!supportedClaims.contains("address")) {
      return false;
    }
    return scopes.contains("address") || requestedUserinfoClaims.hasAddress();
  }

  public boolean shouldAddUpdatedAt() {
    if (!supportedClaims.contains("updated_at")) {
      return false;
    }
    return scopes.contains("profile") || requestedUserinfoClaims.hasUpdatedAt();
  }
}
