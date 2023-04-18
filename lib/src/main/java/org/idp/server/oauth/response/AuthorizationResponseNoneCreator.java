package org.idp.server.oauth.response;

import org.idp.server.oauth.OAuthAuthorizeContext;
import org.idp.server.oauth.request.AuthorizationRequest;
import org.idp.server.type.extension.ResponseModeValue;

public class AuthorizationResponseNoneCreator implements AuthorizationResponseCreator {

  @Override
  public AuthorizationResponse create(OAuthAuthorizeContext context) {
    AuthorizationRequest authorizationRequest = context.authorizationRequest();

    AuthorizationResponseBuilder authorizationResponseBuilder =
        new AuthorizationResponseBuilder(
                authorizationRequest.redirectUri(),
                new ResponseModeValue("?"),
                context.tokenIssuer())
            .add(authorizationRequest.state());

    return authorizationResponseBuilder.build();
  }
}
