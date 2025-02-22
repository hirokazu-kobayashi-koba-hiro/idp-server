package org.idp.sample.presentation.api.oauth;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.idp.sample.domain.model.user.UserRegistration;

public class UserRegistrationRequest {

  @JsonProperty("username")
  String username;

  @JsonProperty("password")
  String password;

  public UserRegistration toUserRegistration() {
    return new UserRegistration(username, password);
  }
}
