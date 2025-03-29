package org.idp.server.core.oauth.grant;

import org.idp.server.core.oauth.OAuthAuthorizeContext;
import org.idp.server.core.oauth.request.AuthorizationRequestIdentifier;
import org.idp.server.core.oauth.response.AuthorizationResponse;
import org.idp.server.core.type.extension.ExpiredAt;
import org.idp.server.core.type.oauth.*;

public class AuthorizationCodeGrantCreator {

  public static AuthorizationCodeGrant create(
      OAuthAuthorizeContext oAuthAuthorizeContext, AuthorizationResponse authorizationResponse) {
    AuthorizationRequestIdentifier authorizationRequestIdentifier =
        oAuthAuthorizeContext.authorizationRequest().identifier();

    AuthorizationGrant authorizationGrant = oAuthAuthorizeContext.toAuthorizationGrant();
    AuthorizationCode authorizationCode = authorizationResponse.authorizationCode();
    ExpiredAt expiredAt = oAuthAuthorizeContext.authorizationCodeGrantExpiresDateTime();

    return new AuthorizationCodeGrant(
        authorizationRequestIdentifier, authorizationGrant, authorizationCode, expiredAt);
  }
}
