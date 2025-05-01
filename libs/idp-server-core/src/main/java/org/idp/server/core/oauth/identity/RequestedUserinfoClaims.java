package org.idp.server.core.oauth.identity;

import java.util.Objects;
import org.idp.server.basic.json.JsonReadable;

public class RequestedUserinfoClaims implements JsonReadable {
  // basic
  ClaimsObject sub;
  ClaimsObject name;
  ClaimsObject givenName;
  ClaimsObject middleName;
  ClaimsObject nickname;
  ClaimsObject preferredUsername;
  ClaimsObject profile;
  ClaimsObject picture;
  ClaimsObject website;
  ClaimsObject email;
  ClaimsObject emailVerified;
  ClaimsObject gender;
  ClaimsObject birthdate;
  ClaimsObject zoneinfo;
  ClaimsObject locale;
  ClaimsObject phoneNumber;
  ClaimsObject phoneNumberVerified;
  ClaimsObject address;
  ClaimsObject updatedAt;
  VerifiedClaimsObject verifiedClaims;

  public RequestedUserinfoClaims() {}

  public ClaimsObject sub() {
    return sub;
  }

  public ClaimsObject name() {
    return name;
  }

  public ClaimsObject givenName() {
    return givenName;
  }

  public ClaimsObject middleName() {
    return middleName;
  }

  public ClaimsObject nickname() {
    return nickname;
  }

  public ClaimsObject preferredUsername() {
    return preferredUsername;
  }

  public ClaimsObject profile() {
    return profile;
  }

  public ClaimsObject picture() {
    return picture;
  }

  public ClaimsObject website() {
    return website;
  }

  public ClaimsObject email() {
    return email;
  }

  public ClaimsObject emailVerified() {
    return emailVerified;
  }

  public ClaimsObject gender() {
    return gender;
  }

  public ClaimsObject birthdate() {
    return birthdate;
  }

  public ClaimsObject zoneinfo() {
    return zoneinfo;
  }

  public ClaimsObject locale() {
    return locale;
  }

  public ClaimsObject phoneNumber() {
    return phoneNumber;
  }

  public ClaimsObject phoneNumberVerified() {
    return phoneNumberVerified;
  }

  public ClaimsObject address() {
    return address;
  }

  public ClaimsObject updatedAt() {
    return updatedAt;
  }

  public VerifiedClaimsObject verifiedClaims() {
    return verifiedClaims;
  }

  public boolean hasSub() {
    return Objects.nonNull(sub);
  }

  public boolean hasName() {
    return Objects.nonNull(name);
  }

  public boolean hasGivenName() {
    return Objects.nonNull(givenName);
  }

  public boolean hasMiddleName() {
    return Objects.nonNull(middleName);
  }

  public boolean hasNickname() {
    return Objects.nonNull(nickname);
  }

  public boolean hasPreferredUsername() {
    return Objects.nonNull(preferredUsername);
  }

  public boolean hasProfile() {
    return Objects.nonNull(profile);
  }

  public boolean hasPicture() {
    return Objects.nonNull(picture);
  }

  public boolean hasWebsite() {
    return Objects.nonNull(website);
  }

  public boolean hasEmail() {
    return Objects.nonNull(email);
  }

  public boolean hasEmailVerified() {
    return Objects.nonNull(emailVerified);
  }

  public boolean hasGender() {
    return Objects.nonNull(gender);
  }

  public boolean hasBirthdate() {
    return Objects.nonNull(birthdate);
  }

  public boolean hasZoneinfo() {
    return Objects.nonNull(zoneinfo);
  }

  public boolean hasLocale() {
    return Objects.nonNull(locale);
  }

  public boolean hasPhoneNumber() {
    return Objects.nonNull(phoneNumber);
  }

  public boolean hasPhoneNumberVerified() {
    return Objects.nonNull(phoneNumberVerified);
  }

  public boolean hasAddress() {
    return Objects.nonNull(address);
  }

  public boolean hasUpdatedAt() {
    return Objects.nonNull(updatedAt);
  }

  public boolean hasVerifiedClaims() {
    return Objects.nonNull(verifiedClaims);
  }
}
