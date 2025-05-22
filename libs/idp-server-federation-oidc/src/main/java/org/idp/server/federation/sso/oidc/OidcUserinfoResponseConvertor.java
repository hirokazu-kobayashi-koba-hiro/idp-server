/*
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package org.idp.server.federation.sso.oidc;

import java.util.HashMap;
import java.util.Objects;
import java.util.UUID;
import org.idp.server.core.oidc.identity.User;

public class OidcUserinfoResponseConvertor {

  User exsistingUser;
  OidcUserinfoResponse userinfoResponse;
  OidcSsoConfiguration configuration;

  public OidcUserinfoResponseConvertor(
      User exsistingUser,
      OidcUserinfoResponse userinfoResponse,
      OidcSsoConfiguration configuration) {
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
    if (Objects.equals(configuration.type(), "facebook")) {
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
