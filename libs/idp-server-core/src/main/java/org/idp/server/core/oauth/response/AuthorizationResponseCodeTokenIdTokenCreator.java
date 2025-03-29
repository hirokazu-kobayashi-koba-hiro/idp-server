package org.idp.server.core.oauth.response;

import org.idp.server.core.oauth.OAuthAuthorizeContext;
import org.idp.server.core.oauth.clientcredentials.ClientCredentials;
import org.idp.server.core.oauth.grant.AuthorizationGrant;
import org.idp.server.core.oauth.identity.IdTokenCreatable;
import org.idp.server.core.oauth.identity.IdTokenCustomClaims;
import org.idp.server.core.oauth.identity.IdTokenCustomClaimsBuilder;
import org.idp.server.core.oauth.request.AuthorizationRequest;
import org.idp.server.core.oauth.token.AccessToken;
import org.idp.server.core.oauth.token.AccessTokenCreatable;
import org.idp.server.core.type.extension.JarmPayload;
import org.idp.server.core.type.extension.ResponseModeValue;
import org.idp.server.core.type.oauth.*;
import org.idp.server.core.type.oidc.IdToken;

public class AuthorizationResponseCodeTokenIdTokenCreator
    implements AuthorizationResponseCreator,
        AuthorizationCodeCreatable,
        AccessTokenCreatable,
        IdTokenCreatable,
        RedirectUriDecidable,
        JarmCreatable {

  @Override
  public AuthorizationResponse create(OAuthAuthorizeContext context) {
    AuthorizationRequest authorizationRequest = context.authorizationRequest();
    AuthorizationCode authorizationCode = createAuthorizationCode();
    AuthorizationGrant authorizationGrant = context.toAuthorizationGrant();

    AccessToken accessToken =
        createAccessToken(
            authorizationGrant,
            context.serverConfiguration(),
            context.clientConfiguration(),
            new ClientCredentials());

    IdTokenCustomClaims idTokenCustomClaims =
        new IdTokenCustomClaimsBuilder()
            .add(authorizationRequest.state())
            .add(authorizationRequest.nonce())
            .add(accessToken.accessTokenEntity())
            .add(authorizationCode)
            .build();

    IdToken idToken =
        createIdToken(
            context.user(),
            context.authentication(),
            context.toAuthorizationGrant(),
            idTokenCustomClaims,
            context.serverConfiguration(),
            context.clientConfiguration());

    AuthorizationResponseBuilder authorizationResponseBuilder =
        new AuthorizationResponseBuilder(
                decideRedirectUri(authorizationRequest, context.clientConfiguration()),
                context.responseMode(),
                ResponseModeValue.fragment(),
                context.tokenIssuer())
            .add(authorizationCode)
            .add(TokenType.Bearer)
            .add(new ExpiresIn(context.serverConfiguration().accessTokenDuration()))
            .add(accessToken)
            .add(authorizationGrant.scopes())
            .add(idToken);

    if (context.hasState()) {
      authorizationResponseBuilder.add(authorizationRequest.state());
    }

    if (context.isJwtMode()) {
      AuthorizationResponse authorizationResponse = authorizationResponseBuilder.build();
      JarmPayload jarmPayload =
          createResponse(
              authorizationResponse, context.serverConfiguration(), context.clientConfiguration());
      authorizationResponseBuilder.add(jarmPayload);
    }

    return authorizationResponseBuilder.build();
  }
}
