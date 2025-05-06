package org.idp.server.core.oidc.response;

import org.idp.server.basic.type.extension.JarmPayload;
import org.idp.server.basic.type.extension.ResponseModeValue;
import org.idp.server.basic.type.oauth.*;
import org.idp.server.basic.type.oidc.IdToken;
import org.idp.server.core.oidc.OAuthAuthorizeContext;
import org.idp.server.core.oidc.clientcredentials.ClientCredentials;
import org.idp.server.core.oidc.grant.AuthorizationGrant;
import org.idp.server.core.oidc.identity.IdTokenCreatable;
import org.idp.server.core.oidc.identity.IdTokenCustomClaims;
import org.idp.server.core.oidc.identity.IdTokenCustomClaimsBuilder;
import org.idp.server.core.oidc.request.AuthorizationRequest;
import org.idp.server.core.oidc.token.AccessToken;
import org.idp.server.core.oidc.token.AccessTokenCreatable;

public class AuthorizationResponseCodeTokenIdTokenCreator implements AuthorizationResponseCreator, AuthorizationCodeCreatable, AccessTokenCreatable, IdTokenCreatable, RedirectUriDecidable, JarmCreatable {

  @Override
  public AuthorizationResponse create(OAuthAuthorizeContext context) {
    AuthorizationRequest authorizationRequest = context.authorizationRequest();
    AuthorizationCode authorizationCode = createAuthorizationCode();
    AuthorizationGrant authorizationGrant = context.authorize();

    AccessToken accessToken = createAccessToken(authorizationGrant, context.serverConfiguration(), context.clientConfiguration(), new ClientCredentials());

    IdTokenCustomClaims idTokenCustomClaims = new IdTokenCustomClaimsBuilder().add(authorizationRequest.state()).add(authorizationRequest.nonce()).add(accessToken.accessTokenEntity()).add(authorizationCode).build();

    IdToken idToken = createIdToken(context.user(), context.authentication(), context.authorize(), idTokenCustomClaims, context.requestedClaimsPayload(), context.serverConfiguration(), context.clientConfiguration());

    AuthorizationResponseBuilder authorizationResponseBuilder = new AuthorizationResponseBuilder(decideRedirectUri(authorizationRequest, context.clientConfiguration()), context.responseMode(), ResponseModeValue.fragment(), context.tokenIssuer()).add(authorizationCode).add(TokenType.Bearer).add(new ExpiresIn(context.serverConfiguration().accessTokenDuration())).add(accessToken).add(authorizationGrant.scopes()).add(idToken);

    if (context.hasState()) {
      authorizationResponseBuilder.add(authorizationRequest.state());
    }

    if (context.isJwtMode()) {
      AuthorizationResponse authorizationResponse = authorizationResponseBuilder.build();
      JarmPayload jarmPayload = createResponse(authorizationResponse, context.serverConfiguration(), context.clientConfiguration());
      authorizationResponseBuilder.add(jarmPayload);
    }

    return authorizationResponseBuilder.build();
  }
}
