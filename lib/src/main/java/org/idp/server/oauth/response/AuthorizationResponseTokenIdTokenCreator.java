package org.idp.server.oauth.response;

import org.idp.server.oauth.OAuthAuthorizeContext;
import org.idp.server.oauth.grant.AuthorizationGrant;
import org.idp.server.oauth.identity.IdTokenCreatable;
import org.idp.server.oauth.identity.IdTokenCustomClaims;
import org.idp.server.oauth.identity.IdTokenCustomClaimsBuilder;
import org.idp.server.oauth.request.AuthorizationRequest;
import org.idp.server.oauth.token.AccessToken;
import org.idp.server.oauth.token.AccessTokenCreatable;
import org.idp.server.type.extension.ResponseModeValue;
import org.idp.server.type.oauth.*;
import org.idp.server.type.oidc.IdToken;

public class AuthorizationResponseTokenIdTokenCreator
    implements AuthorizationResponseCreator,
        AccessTokenCreatable,
        IdTokenCreatable,
        RedirectUriDecidable {

  @Override
  public AuthorizationResponse create(OAuthAuthorizeContext context) {
    AuthorizationRequest authorizationRequest = context.authorizationRequest();
    AuthorizationGrant authorizationGrant = context.toAuthorizationGranted();

    AccessToken accessToken =
        createAccessToken(
            authorizationGrant, context.serverConfiguration(), context.clientConfiguration());
    IdTokenCustomClaims idTokenCustomClaims =
        new IdTokenCustomClaimsBuilder()
            .add(authorizationRequest.state())
            .add(authorizationRequest.nonce())
            .add(accessToken.accessTokenValue())
            .build();
    IdToken idToken =
        createIdToken(
            context.user(),
            context.authentication(),
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
            .add(TokenType.Bearer)
            .add(new ExpiresIn(context.serverConfiguration().accessTokenDuration()))
            .add(accessToken)
            .add(authorizationGrant.scopes())
            .add(idToken);

    return authorizationResponseBuilder.build();
  }
}
