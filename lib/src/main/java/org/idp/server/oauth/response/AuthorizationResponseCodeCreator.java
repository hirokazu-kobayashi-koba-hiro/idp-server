package org.idp.server.oauth.response;

import org.idp.server.oauth.OAuthAuthorizeContext;
import org.idp.server.oauth.request.AuthorizationRequest;
import org.idp.server.type.extension.ResponseModeValue;
import org.idp.server.type.oauth.AuthorizationCode;

public class AuthorizationResponseCodeCreator
    implements AuthorizationResponseCreator, AuthorizationCodeCreatable {

  @Override
  public AuthorizationResponse create(OAuthAuthorizeContext context) {
    AuthorizationRequest authorizationRequest = context.authorizationRequest();
    AuthorizationCode authorizationCode = createAuthorizationCode();
    AuthorizationResponseBuilder authorizationResponseBuilder =
        new AuthorizationResponseBuilder(
                authorizationRequest.redirectUri(),
                new ResponseModeValue("?"),
                context.tokenIssuer())
            .add(authorizationRequest.state())
            .add(authorizationCode);
    return authorizationResponseBuilder.build();
  }
}
