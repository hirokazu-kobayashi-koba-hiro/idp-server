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

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;

public class OidcUserinfoResponse {

  Map<String, Object> values;

  OidcUserinfoResponse() {}

  public OidcUserinfoResponse(Map<String, Object> values) {
    this.values = values;
  }

  public Map<String, Object> toMap() {
    return values;
  }

  public boolean exists() {
    return Objects.nonNull(values) && !values.isEmpty();
  }

  public String sub() {

    if (containsKey("id")) {
      return (String) values.get("id");
    }
    return optValueAsString("sub", "");
  }

  public String name() {
    return optValueAsString("name", "");
  }

  public String givenName() {
    return optValueAsString("given_name", "");
  }

  public String familyName() {
    return optValueAsString("family_name", "");
  }

  public String middleName() {
    return optValueAsString("middle_name", "");
  }

  public String nickname() {
    return optValueAsString("nickname", "");
  }

  public String preferredUsername() {
    return optValueAsString("preferred_username", "");
  }

  public String profile() {
    return optValueAsString("profile", "");
  }

  public String picture() {
    return optValueAsString("picture", "");
  }

  public String website() {
    return optValueAsString("website", "");
  }

  public String email() {
    return optValueAsString("email", "");
  }

  public Boolean emailVerified() {
    return optValueAsBoolean("email_verified", false);
  }

  public String gender() {
    return optValueAsString("gender", "");
  }

  public String birthdate() {
    return optValueAsString("birthdate", "");
  }

  public String zoneinfo() {
    return optValueAsString("zoneinfo", "");
  }

  public String locale() {
    return optValueAsString("locale", "");
  }

  public String phoneNumber() {
    return optValueAsString("phone_number", "");
  }

  public Boolean phoneNumberVerified() {
    return optValueAsBoolean("phone_number_verified", false);
  }

  public LocalDateTime updatedAt() {
    return LocalDateTime.now();
  }

  public String optValueAsString(String key, String defaultValue) {
    if (containsKey(key)) {
      return (String) values.get(key);
    }
    return defaultValue;
  }

  public Boolean optValueAsBoolean(String key, boolean defaultValue) {
    if (containsKey(key)) {
      return (Boolean) values.get(key);
    }
    return defaultValue;
  }

  public boolean containsKey(String key) {
    return values.containsKey(key);
  }
}
