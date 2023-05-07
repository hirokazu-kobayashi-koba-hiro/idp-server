package org.idp.server.userinfo.verifier;

import java.time.LocalDateTime;
import org.idp.server.basic.date.SystemDateTime;
import org.idp.server.token.OAuthToken;
import org.idp.server.tokenintrospection.exception.TokenInvalidException;

public class UserinfoVerifier {

  OAuthToken oAuthToken;

  public UserinfoVerifier(OAuthToken oAuthToken) {
    this.oAuthToken = oAuthToken;
  }

  public void verify() {
    if (!oAuthToken.exists()) {
      throw new TokenInvalidException("not found token");
    }
    LocalDateTime now = SystemDateTime.now();
    if (oAuthToken.isExpire(now)) {
      throw new TokenInvalidException("token is expired");
    }
  }
}
