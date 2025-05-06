package org.idp.server.adapters.springboot.restapi.management;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.idp.server.core.identity.User;

public class UserResponse {
  @JsonProperty("")
  String sub;

  @JsonProperty("")
  String name;

  @JsonProperty("")
  String givenName;

  @JsonProperty("")
  String familyName;

  @JsonProperty("")
  String middleName;

  @JsonProperty("")
  String nickname;

  @JsonProperty("")
  String preferredUsername;

  @JsonProperty("")
  String profile;

  @JsonProperty("")
  String picture;

  @JsonProperty("")
  String website;

  @JsonProperty("")
  String email;

  @JsonProperty("")
  Boolean emailVerified;

  @JsonProperty("")
  String gender;

  @JsonProperty("")
  String birthdate;

  @JsonProperty("")
  String zoneinfo;

  @JsonProperty("")
  String locale;

  @JsonProperty("")
  String phoneNumber;

  @JsonProperty("")
  Boolean phoneNumberVerified;

  // TODO address
  @JsonProperty("updated_at")
  String updatedAt;

  @JsonProperty("password")
  String password;

  // @JsonProperty("")
  // Map<String, Object> customProperties;
  // @JsonProperty("")
  // List<Map<String, Object>> credentials;

  public UserResponse(User user) {
    this.sub = user.sub();
    this.name = user.name();
    this.givenName = user.givenName();
    this.familyName = user.familyName();
    this.middleName = user.middleName();
    this.nickname = user.nickname();
    this.preferredUsername = user.preferredUsername();
    this.profile = user.profile();
    this.picture = user.picture();
    this.website = user.website();
    this.email = user.email();
    this.emailVerified = user.emailVerified();
    this.gender = user.gender();
    this.birthdate = user.birthdate();
    this.zoneinfo = user.zoneinfo();
    this.locale = user.locale();
    this.phoneNumber = user.phoneNumber();
    this.phoneNumberVerified = user.phoneNumberVerified();
    this.updatedAt = user.updatedAt().toString();
    this.password = "****";
  }
}
