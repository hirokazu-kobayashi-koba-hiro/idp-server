package org.idp.server.oauth.response;

import org.idp.server.oauth.OAuthAuthorizeContext;
import org.idp.server.oauth.clientcredentials.ClientCredentials;
import org.idp.server.oauth.grant.AuthorizationGrant;
import org.idp.server.oauth.request.AuthorizationRequest;
import org.idp.server.oauth.token.AccessToken;
import org.idp.server.oauth.token.AccessTokenCreatable;
import org.idp.server.type.extension.JarmPayload;
import org.idp.server.type.extension.ResponseModeValue;
import org.idp.server.type.oauth.*;

public class AuthorizationResponseTokenCreator
    implements AuthorizationResponseCreator,
        AccessTokenCreatable,
        RedirectUriDecidable,
        JarmCreatable {

  @Override
  public AuthorizationResponse create(OAuthAuthorizeContext context) {
    AuthorizationRequest authorizationRequest = context.authorizationRequest();

    AuthorizationGrant authorizationGrant = context.toAuthorizationGranted();

    AccessToken accessToken =
        createAccessToken(
            authorizationGrant,
            context.serverConfiguration(),
            context.clientConfiguration(),
            new ClientCredentials());
    AuthorizationResponseBuilder authorizationResponseBuilder =
        new AuthorizationResponseBuilder(
                decideRedirectUri(authorizationRequest, context.clientConfiguration()),
                context.responseMode(),
                new ResponseModeValue("#"),
                context.tokenIssuer())
            .add(authorizationRequest.state())
            .add(TokenType.Bearer)
            .add(new ExpiresIn(context.serverConfiguration().accessTokenDuration()))
            .add(accessToken)
            .add(authorizationGrant.scopes());

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
