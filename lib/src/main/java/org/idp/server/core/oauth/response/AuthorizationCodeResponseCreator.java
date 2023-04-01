package org.idp.server.core.oauth.response;

import org.idp.server.core.oauth.OAuthAuthorizeContext;
import org.idp.server.core.oauth.request.AuthorizationRequest;
import org.idp.server.core.type.AuthorizationCode;
import org.idp.server.core.type.ResponseModeValue;

public class AuthorizationCodeResponseCreator
    implements AuthorizationResponseCreator, AuthorizationCodeCreatable {

  @Override
  public AuthorizationResponse create(OAuthAuthorizeContext context) {
    AuthorizationRequest authorizationRequest = context.authorizationRequest();
    AuthorizationCode authorizationCode = createAuthorizationCode();
    AuthorizationResponseBuilder authorizationResponseBuilder =
        new AuthorizationResponseBuilder(
            authorizationRequest.redirectUri(), new ResponseModeValue("?"), context.tokenIssuer());
    authorizationResponseBuilder.add(authorizationRequest.state());
    authorizationResponseBuilder.add(authorizationCode);
    return authorizationResponseBuilder.build();
  }
}
