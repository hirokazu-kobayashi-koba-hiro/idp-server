package org.idp.server.oauth.response;

import org.idp.server.oauth.OAuthAuthorizeContext;
import org.idp.server.oauth.grant.AuthorizationGrant;
import org.idp.server.oauth.request.AuthorizationRequest;
import org.idp.server.oauth.token.AccessToken;
import org.idp.server.oauth.token.AccessTokenCreatable;
import org.idp.server.oauth.token.AccessTokenPayload;
import org.idp.server.type.extension.ResponseModeValue;
import org.idp.server.type.oauth.*;

public class AuthorizationResponseCodeTokenCreator
    implements AuthorizationResponseCreator, AuthorizationCodeCreatable, AccessTokenCreatable {

  @Override
  public AuthorizationResponse create(OAuthAuthorizeContext context) {
    AuthorizationRequest authorizationRequest = context.authorizationRequest();
    AuthorizationCode authorizationCode = createAuthorizationCode();
    AuthorizationGrant authorizationGrant = context.toAuthorizationGranted();
    AccessTokenPayload accessTokenPayload =
        createAccessTokenPayload(
            authorizationGrant, context.serverConfiguration(), context.clientConfiguration());
    AccessToken accessToken =
        createAccessToken(
            accessTokenPayload, context.serverConfiguration(), context.clientConfiguration());

    AuthorizationResponseBuilder authorizationResponseBuilder =
        new AuthorizationResponseBuilder(
                authorizationRequest.redirectUri(),
                new ResponseModeValue("#"),
                context.tokenIssuer())
            .add(authorizationRequest.state())
            .add(authorizationCode)
            .add(TokenType.Bearer)
            .add(new ExpiresIn(context.serverConfiguration().accessTokenDuration()))
            .add(accessToken);

    return authorizationResponseBuilder.build();
  }
}
