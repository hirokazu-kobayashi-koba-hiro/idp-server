package org.idp.server.oauth.identity;

import org.idp.server.type.oauth.Scopes;

public class UserinfoIndividualClaimsDecider {
  Scopes scopes;
  UserinfoClaims userinfoClaims;

  public UserinfoIndividualClaimsDecider(Scopes scopes, UserinfoClaims userinfoClaims) {
    this.scopes = scopes;
    this.userinfoClaims = userinfoClaims;
  }

  public boolean shouldAddSub() {
    return true;
  }

  public boolean shouldAddName() {
    return scopes.contains("profile");
  }

  public boolean shouldAddGivenName() {
    return scopes.contains("profile");
  }

  public boolean shouldAddMiddleName() {
    return scopes.contains("profile");
  }

  public boolean shouldAddNickname() {
    return scopes.contains("profile");
  }

  public boolean shouldAddPreferredUsername() {
    return scopes.contains("profile");
  }

  public boolean shouldAddProfile() {
    return scopes.contains("profile");
  }

  public boolean shouldAddPicture() {
    return scopes.contains("profile");
  }

  public boolean shouldAddWebsite() {
    return scopes.contains("profile");
  }

  public boolean shouldAddEmail() {
    return scopes.contains("email");
  }

  public boolean shouldAddEmailVerified() {
    return scopes.contains("email");
  }

  public boolean shouldAddGender() {
    return scopes.contains("profile");
  }

  public boolean shouldAddBirthdate() {
    return scopes.contains("profile");
  }

  public boolean shouldAddZoneinfo() {
    return scopes.contains("profile");
  }

  public boolean shouldAddLocale() {
    return scopes.contains("profile");
  }

  public boolean shouldAddPhoneNumber() {
    return scopes.contains("phone");
  }

  public boolean shouldAddPhoneNumberVerified() {
    return scopes.contains("phone");
  }

  public boolean shouldAddAddress() {
    return scopes.contains("address");
  }

  public boolean shouldAddUpdatedAt() {
    return scopes.contains("profile");
  }
}
