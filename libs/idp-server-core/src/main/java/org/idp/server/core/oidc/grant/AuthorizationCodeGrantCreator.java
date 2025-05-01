package org.idp.server.core.oidc.grant;

import org.idp.server.core.oidc.OAuthAuthorizeContext;
import org.idp.server.core.oidc.request.AuthorizationRequestIdentifier;
import org.idp.server.core.oidc.response.AuthorizationResponse;
import org.idp.server.basic.type.extension.ExpiredAt;
import org.idp.server.basic.type.oauth.*;

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
