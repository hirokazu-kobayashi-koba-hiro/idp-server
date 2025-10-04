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

package org.idp.server.platform.security.event;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.idp.server.platform.json.JsonReadable;
import org.idp.server.platform.uuid.UuidConvertable;

public class SecurityEventUser implements UuidConvertable, JsonReadable {
  String sub;
  String name;
  String exSub;
  String email;
  String phoneNumber;
  String givenName;
  String familyName;
  String preferredUsername;
  String profile;
  String picture;
  String website;
  String gender;
  String birthdate;
  String zoneinfo;
  String locale;
  List<String> roles;
  List<String> permissions;
  String currentTenant;
  List<String> assignedTenants;

  public SecurityEventUser() {}

  public SecurityEventUser(
      String sub, String name, String exSub, String email, String phoneNumber) {
    this.sub = sub;
    this.name = name;
    this.exSub = exSub;
    this.email = email;
    this.phoneNumber = phoneNumber;
  }

  public SecurityEventUser(
      String sub,
      String name,
      String exSub,
      String email,
      String phoneNumber,
      String givenName,
      String familyName,
      String preferredUsername,
      String profile,
      String picture,
      String website,
      String gender,
      String birthdate,
      String zoneinfo,
      String locale,
      List<String> roles,
      List<String> permissions,
      String currentTenant,
      List<String> assignedTenants) {
    this.sub = sub;
    this.name = name;
    this.exSub = exSub;
    this.email = email;
    this.phoneNumber = phoneNumber;
    this.givenName = givenName;
    this.familyName = familyName;
    this.preferredUsername = preferredUsername;
    this.profile = profile;
    this.picture = picture;
    this.website = website;
    this.gender = gender;
    this.birthdate = birthdate;
    this.zoneinfo = zoneinfo;
    this.locale = locale;
    this.roles = roles;
    this.permissions = permissions;
    this.currentTenant = currentTenant;
    this.assignedTenants = assignedTenants;
  }

  public Map<String, Object> toMap() {
    HashMap<String, Object> result = new HashMap<>();
    if (sub != null) {
      result.put("sub", sub);
    }
    if (name != null) {
      result.put("name", name);
    }
    if (exSub != null) {
      result.put("ex_sub", exSub);
    }
    if (email != null) {
      result.put("email", email);
    }
    if (phoneNumber != null) {
      result.put("phone_number", phoneNumber);
    }
    if (givenName != null) {
      result.put("given_name", givenName);
    }
    if (familyName != null) {
      result.put("family_name", familyName);
    }
    if (preferredUsername != null) {
      result.put("preferred_username", preferredUsername);
    }
    if (profile != null) {
      result.put("profile", profile);
    }
    if (picture != null) {
      result.put("picture", picture);
    }
    if (website != null) {
      result.put("website", website);
    }
    if (gender != null) {
      result.put("gender", gender);
    }
    if (birthdate != null) {
      result.put("birthdate", birthdate);
    }
    if (zoneinfo != null) {
      result.put("zoneinfo", zoneinfo);
    }
    if (locale != null) {
      result.put("locale", locale);
    }
    if (roles != null) {
      result.put("roles", roles);
    }
    if (permissions != null) {
      result.put("permissions", permissions);
    }
    if (currentTenant != null) {
      result.put("current_tenant", currentTenant);
    }
    if (assignedTenants != null) {
      result.put("assigned_tenants", assignedTenants);
    }

    return result;
  }

  public String sub() {
    return sub;
  }

  public UUID subAsUuid() {
    return convertUuid(sub);
  }

  public String name() {
    return name;
  }

  public String exSub() {
    return exSub;
  }

  public String email() {
    return email;
  }

  public String phoneNumber() {
    return phoneNumber;
  }

  public String givenName() {
    return givenName;
  }

  public String familyName() {
    return familyName;
  }

  public String preferredUsername() {
    return preferredUsername;
  }

  public String profile() {
    return profile;
  }

  public String picture() {
    return picture;
  }

  public String website() {
    return website;
  }

  public String gender() {
    return gender;
  }

  public String birthdate() {
    return birthdate;
  }

  public String zoneinfo() {
    return zoneinfo;
  }

  public String locale() {
    return locale;
  }

  public List<String> roles() {
    return roles;
  }

  public List<String> permissions() {
    return permissions;
  }

  public String currentTenant() {
    return currentTenant;
  }

  public List<String> assignedTenants() {
    return assignedTenants;
  }

  public boolean exists() {
    return Objects.nonNull(sub) && !sub.isEmpty();
  }
}
