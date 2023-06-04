package org.idp.server.oauth;

import java.io.Serializable;
import java.time.LocalDateTime;
import org.idp.server.basic.date.SystemDateTime;
import org.idp.server.oauth.authentication.Authentication;
import org.idp.server.oauth.identity.User;
import org.idp.server.oauth.request.AuthorizationRequest;

public class OAuthSession implements Serializable {
  User user;
  Authentication authentication;

  public OAuthSession(User user, Authentication authentication) {
    this.user = user;
    this.authentication = authentication;
  }

  public User user() {
    return user;
  }

  public Authentication authentication() {
    return authentication;
  }

  public boolean isValid(AuthorizationRequest request) {
    LocalDateTime authenticationTime = authentication.time();
    LocalDateTime now = SystemDateTime.now();
    if (now.isAfter(authenticationTime.plusSeconds(request.maxAge().toLongValue()))) {
      return false;
    }
    // TODO logic
    return true;
  }
}
