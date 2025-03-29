package org.idp.server.core.oauth.response;

import org.idp.server.core.oauth.OAuthAuthorizeContext;
import org.idp.server.core.oauth.clientcredentials.ClientCredentials;
import org.idp.server.core.oauth.grant.AuthorizationGrant;
import org.idp.server.core.oauth.identity.IdTokenCreatable;
import org.idp.server.core.oauth.identity.IdTokenCustomClaims;
import org.idp.server.core.oauth.identity.IdTokenCustomClaimsBuilder;
import org.idp.server.core.oauth.request.AuthorizationRequest;
import org.idp.server.core.oauth.token.VpTokenCreatable;
import org.idp.server.core.type.extension.JarmPayload;
import org.idp.server.core.type.extension.ResponseModeValue;
import org.idp.server.core.type.oidc.IdToken;
import org.idp.server.core.type.verifiablepresentation.VpToken;

public class AuthorizationResponseVpTokenIdTokenCreator
    implements AuthorizationResponseCreator,
        VpTokenCreatable,
        IdTokenCreatable,
        RedirectUriDecidable,
        JarmCreatable {

  @Override
  public AuthorizationResponse create(OAuthAuthorizeContext context) {
    AuthorizationRequest authorizationRequest = context.authorizationRequest();
    AuthorizationGrant authorizationGrant = context.toAuthorizationGrant();

    VpToken vpToken =
        createVpToken(
            context.user(),
            authorizationGrant,
            context.serverConfiguration(),
            context.clientConfiguration(),
            new ClientCredentials());
    IdTokenCustomClaims idTokenCustomClaims =
        new IdTokenCustomClaimsBuilder()
            .add(authorizationRequest.state())
            .add(authorizationRequest.nonce())
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
            .add(vpToken)
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
