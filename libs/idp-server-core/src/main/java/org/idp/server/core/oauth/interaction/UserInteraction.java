package org.idp.server.core.oauth.interaction;

import org.idp.server.core.oauth.authentication.Authentication;
import org.idp.server.core.oauth.identity.User;

public class UserInteraction {
  User user;
  Authentication authentication;

  public UserInteraction(User user, Authentication authentication) {
    this.user = user;
    this.authentication = authentication;
  }

  public User user() {
    return user;
  }

  public Authentication authentication() {
    return authentication;
  }
}
