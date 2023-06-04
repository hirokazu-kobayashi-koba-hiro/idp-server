package org.idp.server.oauth.interaction;

import org.idp.server.oauth.authentication.Authentication;
import org.idp.server.oauth.identity.User;
import org.idp.server.type.extension.CustomProperties;

public class UserInteraction {
  User user;
  Authentication authentication;
  CustomProperties customProperties;

  public UserInteraction(User user, Authentication authentication) {
    this(user, authentication, new CustomProperties());
  }

  public UserInteraction(
      User user, Authentication authentication, CustomProperties customProperties) {
    this.user = user;
    this.authentication = authentication;
    this.customProperties = customProperties;
  }

  public User user() {
    return user;
  }

  public Authentication authentication() {
    return authentication;
  }

  public CustomProperties customProperties() {
    return customProperties;
  }
}
