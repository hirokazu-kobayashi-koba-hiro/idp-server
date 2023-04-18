package org.idp.server.oauth.response;

import org.idp.server.oauth.OAuthAuthorizeContext;
import org.idp.server.oauth.authentication.Authentication;
import org.idp.server.oauth.identity.IdTokenCreatable;
import org.idp.server.oauth.identity.User;
import org.idp.server.oauth.request.AuthorizationRequest;
import org.idp.server.type.extension.ResponseModeValue;
import org.idp.server.type.oauth.*;
import org.idp.server.type.oidc.IdToken;

public class AuthorizationResponseCodeIdTokenCreator
    implements AuthorizationResponseCreator, AuthorizationCodeCreatable, IdTokenCreatable {

  @Override
  public AuthorizationResponse create(OAuthAuthorizeContext context) {
    AuthorizationRequest authorizationRequest = context.authorizationRequest();
    AuthorizationCode authorizationCode = createAuthorizationCode();
    User user = context.user();

    IdToken idToken =
        createIdToken(
            authorizationRequest,
            authorizationCode,
            new AccessToken(),
            user,
            new Authentication(),
            context.serverConfiguration(),
            context.clientConfiguration());
    AuthorizationResponseBuilder authorizationResponseBuilder =
        new AuthorizationResponseBuilder(
                authorizationRequest.redirectUri(),
                new ResponseModeValue("#"),
                context.tokenIssuer())
            .add(authorizationRequest.state())
            .add(authorizationCode)
            .add(idToken);

    return authorizationResponseBuilder.build();
  }
}
