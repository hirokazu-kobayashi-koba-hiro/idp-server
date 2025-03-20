package org.idp.server.core.federation;

import java.util.HashMap;
import java.util.UUID;
import org.idp.server.core.oauth.identity.User;

public class FederationUserinfoResponseConvertor {

  User exsistingUser;
  FederationUserinfoResponse userinfoResponse;
  FederatableIdProviderConfiguration configuration;

  public FederationUserinfoResponseConvertor(
      User exsistingUser,
      FederationUserinfoResponse userinfoResponse,
      FederatableIdProviderConfiguration configuration) {
    this.exsistingUser = exsistingUser;
    this.userinfoResponse = userinfoResponse;
    this.configuration = configuration;
  }

  public User convert() {
    User user = new User();
    if (exsistingUser.exists()) {
      user.setSub(exsistingUser.sub());
    } else {
      user.setSub(UUID.randomUUID().toString());
    }
    user.setProviderId(configuration.issuerName());
    user.setProviderUserId(userinfoResponse.sub());
    user.setName(userinfoResponse.name());
    user.setFamilyName(userinfoResponse.familyName());
    user.setGivenName(userinfoResponse.givenName());
    user.setFamilyName(userinfoResponse.familyName());
    user.setNickname(userinfoResponse.nickname());
    user.setMiddleName(userinfoResponse.middleName());
    user.setPreferredUsername(userinfoResponse.preferredUsername());
    user.setProfile(userinfoResponse.profile());
    if (configuration.isFacebook()) {
      user.setPicture("");
    } else {
      user.setPicture(userinfoResponse.picture());
    }
    user.setWebsite(userinfoResponse.website());
    user.setEmail(userinfoResponse.email());
    user.setEmailVerified(userinfoResponse.emailVerified());
    user.setGender(userinfoResponse.gender());
    user.setBirthdate(userinfoResponse.birthdate());
    user.setZoneinfo(userinfoResponse.zoneinfo());
    user.setPhoneNumber(userinfoResponse.phoneNumber());
    user.setPhoneNumberVerified(userinfoResponse.phoneNumberVerified());
    user.setProviderOriginalPayload(new HashMap<>(userinfoResponse.toMap()));

    return user;
  }
}
