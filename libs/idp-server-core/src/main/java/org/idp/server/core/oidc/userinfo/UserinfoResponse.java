package org.idp.server.core.oidc.userinfo;

import java.util.Map;
import org.idp.server.core.oidc.identity.User;

public class UserinfoResponse {

  User user;
  Map<String, Object> response;

  public UserinfoResponse() {}

  public UserinfoResponse(User user, Map<String, Object> response) {
    this.user = user;
    this.response = response;
  }

  public User user() {
    return user;
  }

  public Map<String, Object> response() {
    return response;
  }
}
