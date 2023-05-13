package org.idp.server.oauth.response;

import org.idp.server.oauth.OAuthAuthorizeContext;
import org.idp.server.oauth.authentication.Authentication;
import org.idp.server.oauth.identity.IdTokenCreatable;
import org.idp.server.oauth.identity.IdTokenCustomClaims;
import org.idp.server.oauth.identity.IdTokenCustomClaimsBuilder;
import org.idp.server.oauth.request.AuthorizationRequest;
import org.idp.server.type.extension.ResponseModeValue;
import org.idp.server.type.oauth.*;
import org.idp.server.type.oidc.IdToken;

public class AuthorizationResponseIdTokenCreator
    implements AuthorizationResponseCreator, IdTokenCreatable, RedirectUriDecidable {

  @Override
  public AuthorizationResponse create(OAuthAuthorizeContext context) {
    AuthorizationRequest authorizationRequest = context.authorizationRequest();
    IdTokenCustomClaims idTokenCustomClaims =
        new IdTokenCustomClaimsBuilder()
            .add(authorizationRequest.state())
            .add(authorizationRequest.nonce())
            .build();
    IdToken idToken =
        createIdToken(
            context.user(),
            new Authentication(),
            context.scopes(),
            context.idTokenClaims(),
            idTokenCustomClaims,
            context.serverConfiguration(),
            context.clientConfiguration());
    AuthorizationResponseBuilder authorizationResponseBuilder =
        new AuthorizationResponseBuilder(
                decideRedirectUri(authorizationRequest, context.clientConfiguration()),
                new ResponseModeValue("#"),
                context.tokenIssuer())
            .add(authorizationRequest.state())
            .add(idToken);

    return authorizationResponseBuilder.build();
  }
}
