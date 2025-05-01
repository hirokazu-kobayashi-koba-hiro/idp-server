package org.idp.server.core.token.tokenintrospection.verifier;

import java.time.LocalDateTime;
import org.idp.server.basic.date.SystemDateTime;
import org.idp.server.core.token.OAuthToken;
import org.idp.server.core.token.handler.tokenintrospection.io.TokenIntrospectionRequestStatus;

public class TokenIntrospectionVerifier {

  OAuthToken oAuthToken;

  public TokenIntrospectionVerifier(OAuthToken oAuthToken) {
    this.oAuthToken = oAuthToken;
  }

  public TokenIntrospectionRequestStatus verify() {

    if (!oAuthToken.exists()) {
      return TokenIntrospectionRequestStatus.INVALID_TOKEN;
    }
    LocalDateTime now = SystemDateTime.now();
    if (oAuthToken.isExpire(now)) {
      return TokenIntrospectionRequestStatus.EXPIRED_TOKEN;
    }

    return TokenIntrospectionRequestStatus.OK;
  }
}
