package org.idp.server.core.oauth.identity;

import java.util.Objects;
import org.idp.server.core.basic.json.JsonReadable;

public class ClaimsPayload implements JsonReadable {
  UserinfoClaims userinfo = new UserinfoClaims();
  IdTokenClaims idToken = new IdTokenClaims();

  public ClaimsPayload() {}

  public UserinfoClaims userinfo() {
    return userinfo;
  }

  public IdTokenClaims idToken() {
    return idToken;
  }

  public boolean hasUserinfo() {
    return Objects.nonNull(userinfo);
  }

  public boolean hasIdToken() {
    return Objects.nonNull(idToken);
  }

  public boolean exists() {
    return hasUserinfo() || hasIdToken();
  }
}
