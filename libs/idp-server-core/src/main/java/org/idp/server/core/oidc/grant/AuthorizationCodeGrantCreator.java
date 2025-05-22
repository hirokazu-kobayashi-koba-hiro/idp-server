/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.oidc.grant;

import org.idp.server.basic.type.extension.ExpiredAt;
import org.idp.server.basic.type.oauth.*;
import org.idp.server.core.oidc.OAuthAuthorizeContext;
import org.idp.server.core.oidc.request.AuthorizationRequestIdentifier;
import org.idp.server.core.oidc.response.AuthorizationResponse;

public class AuthorizationCodeGrantCreator {

  public static AuthorizationCodeGrant create(
      OAuthAuthorizeContext oAuthAuthorizeContext, AuthorizationResponse authorizationResponse) {
    AuthorizationRequestIdentifier authorizationRequestIdentifier =
        oAuthAuthorizeContext.authorizationRequest().identifier();

    AuthorizationGrant authorizationGrant = oAuthAuthorizeContext.authorize();
    AuthorizationCode authorizationCode = authorizationResponse.authorizationCode();
    ExpiredAt expiredAt = oAuthAuthorizeContext.authorizationCodeGrantExpiresDateTime();

    return new AuthorizationCodeGrant(
        authorizationRequestIdentifier, authorizationGrant, authorizationCode, expiredAt);
  }
}
