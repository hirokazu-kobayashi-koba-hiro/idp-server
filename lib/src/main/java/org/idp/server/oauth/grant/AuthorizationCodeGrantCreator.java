package org.idp.server.oauth.grant;

import org.idp.server.oauth.OAuthAuthorizeContext;
import org.idp.server.oauth.request.AuthorizationRequestIdentifier;
import org.idp.server.oauth.response.AuthorizationResponse;
import org.idp.server.type.extension.CustomProperties;
import org.idp.server.type.oauth.*;

public class AuthorizationCodeGrantCreator {

  public static AuthorizationCodeGrant create(
      OAuthAuthorizeContext oAuthAuthorizeContext, AuthorizationResponse authorizationResponse) {
    AuthorizationRequestIdentifier authorizationRequestIdentifier =
        oAuthAuthorizeContext.authorizationRequest().identifier();
    Subject subject = oAuthAuthorizeContext.subject();
    ClientId clientId = oAuthAuthorizeContext.clientConfiguration().clientId();
    Scopes scopes = oAuthAuthorizeContext.scopes();
    CustomProperties customProperties = oAuthAuthorizeContext.customProperties();
    AuthorizationGranted authorizationGranted =
        new AuthorizationGranted(subject, clientId, scopes, customProperties);
    AuthorizationCode authorizationCode = authorizationResponse.authorizationCode();
    ExpiredAt expiredAt = oAuthAuthorizeContext.authorizationCodeGrantExpiresDateTime();

    return new AuthorizationCodeGrant(
        authorizationRequestIdentifier, authorizationGranted, authorizationCode, expiredAt);
  }
}
