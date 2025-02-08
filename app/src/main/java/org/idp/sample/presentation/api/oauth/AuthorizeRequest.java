package org.idp.sample.presentation.api.oauth;

import com.fasterxml.jackson.annotation.JsonProperty;

public class AuthorizeRequest {
  @JsonProperty("session_key")
  String sessionKey;

  @JsonProperty("username")
  String username;

  @JsonProperty("password")
  String password;

  public String sessionKey() {
    return sessionKey;
  }

  public String username() {
    return username;
  }

  public String password() {
    return password;
  }
}
