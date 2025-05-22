/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.oidc.token.tokenintrospection.verifier;

import java.time.LocalDateTime;
import org.idp.server.core.oidc.token.OAuthToken;
import org.idp.server.core.oidc.token.handler.tokenintrospection.io.TokenIntrospectionRequestStatus;
import org.idp.server.platform.date.SystemDateTime;

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
