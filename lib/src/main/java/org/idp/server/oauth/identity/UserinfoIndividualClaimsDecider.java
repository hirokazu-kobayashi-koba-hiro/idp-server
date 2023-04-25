package org.idp.server.oauth.identity;

import java.util.HashSet;
import java.util.Set;
import org.idp.server.type.oauth.Scopes;
import org.idp.server.type.oidc.Claims;

public class UserinfoIndividualClaimsDecider {
  Scopes scopes;
  UserinfoClaims userinfoClaims;

  public UserinfoIndividualClaimsDecider(Scopes scopes, UserinfoClaims userinfoClaims) {
    this.scopes = scopes;
    this.userinfoClaims = userinfoClaims;
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
    return scopes.contains("profile") || userinfoClaims.hasName();
  }

  public boolean shouldAddGivenName() {
    return scopes.contains("profile") || userinfoClaims.hasGivenName();
  }

  public boolean shouldAddMiddleName() {
    return scopes.contains("profile") || userinfoClaims.hasMiddleName();
  }

  public boolean shouldAddNickname() {
    return scopes.contains("profile") || userinfoClaims.hasNickname();
  }

  public boolean shouldAddPreferredUsername() {
    return scopes.contains("profile") || userinfoClaims.hasPreferredUsername();
  }

  public boolean shouldAddProfile() {
    return scopes.contains("profile") || userinfoClaims.hasProfile();
  }

  public boolean shouldAddPicture() {
    return scopes.contains("profile") || userinfoClaims.hasPicture();
  }

  public boolean shouldAddWebsite() {
    return scopes.contains("profile") || userinfoClaims.hasWebsite();
  }

  public boolean shouldAddEmail() {
    return scopes.contains("email") || userinfoClaims.hasEmail();
  }

  public boolean shouldAddEmailVerified() {
    return scopes.contains("email") || userinfoClaims.hasEmailVerified();
  }

  public boolean shouldAddGender() {
    return scopes.contains("profile") || userinfoClaims.hasGender();
  }

  public boolean shouldAddBirthdate() {
    return scopes.contains("profile") || userinfoClaims.hasBirthdate();
  }

  public boolean shouldAddZoneinfo() {
    return scopes.contains("profile") || userinfoClaims.hasZoneinfo();
  }

  public boolean shouldAddLocale() {
    return scopes.contains("profile") || userinfoClaims.hasLocale();
  }

  public boolean shouldAddPhoneNumber() {
    return scopes.contains("phone") || userinfoClaims.hasPhoneNumber();
  }

  public boolean shouldAddPhoneNumberVerified() {
    return scopes.contains("phone") || userinfoClaims.hasPhoneNumberVerified();
  }

  public boolean shouldAddAddress() {
    return scopes.contains("address") || userinfoClaims.hasAddress();
  }

  public boolean shouldAddUpdatedAt() {
    return scopes.contains("profile") || userinfoClaims.hasUpdatedAt();
  }
}
