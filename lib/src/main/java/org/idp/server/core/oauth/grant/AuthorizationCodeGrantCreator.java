package org.idp.server.core.oauth.grant;

import org.idp.server.core.oauth.OAuthAuthorizeContext;
import org.idp.server.core.oauth.request.AuthorizationRequestIdentifier;
import org.idp.server.core.oauth.response.AuthorizationResponse;
import org.idp.server.core.type.*;

public class AuthorizationCodeGrantCreator {

  public static AuthorizationCodeGrant create(
      OAuthAuthorizeContext oAuthAuthorizeContext, AuthorizationResponse authorizationResponse) {
    AuthorizationRequestIdentifier authorizationRequestIdentifier =
        oAuthAuthorizeContext.authorizationRequest().identifier();
    Subject subject = new Subject(oAuthAuthorizeContext.user().sub());
    ClientId clientId = oAuthAuthorizeContext.clientConfiguration().clientId();
    AuthorizationGranted authorizationGranted = new AuthorizationGranted(subject, clientId);
    AuthorizationCode authorizationCode = authorizationResponse.authorizationCode();
    ExpiredAt expiredAt = oAuthAuthorizeContext.authorizationCodeGrantExpiresDateTime();
    CustomProperties customProperties = oAuthAuthorizeContext.customProperties();
    return new AuthorizationCodeGrant(
        authorizationRequestIdentifier,
        authorizationGranted,
        authorizationCode,
        expiredAt,
        customProperties);
  }
}
