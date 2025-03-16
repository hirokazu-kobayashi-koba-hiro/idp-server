package org.idp.server.adapters.springboot.restapi.oauth;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.idp.server.core.user.UserRegistration;

public class UserRegistrationRequest {

  @JsonProperty("username")
  String username;

  @JsonProperty("password")
  String password;

  public UserRegistration toUserRegistration() {
    return new UserRegistration(username, password);
  }
}
