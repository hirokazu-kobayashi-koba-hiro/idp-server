package org.idp.server.oauth.response;

import org.idp.server.oauth.OAuthAuthorizeContext;
import org.idp.server.oauth.grant.AuthorizationGranted;
import org.idp.server.oauth.identity.User;
import org.idp.server.oauth.request.AuthorizationRequest;
import org.idp.server.token.AccessTokenCreatable;
import org.idp.server.token.AccessTokenPayload;
import org.idp.server.type.extension.CustomProperties;
import org.idp.server.type.extension.ResponseModeValue;
import org.idp.server.type.oauth.*;

public class AuthorizationResponseCodeTokenCreator
    implements AuthorizationResponseCreator, AuthorizationCodeCreatable, AccessTokenCreatable {

  @Override
  public AuthorizationResponse create(OAuthAuthorizeContext context) {
    AuthorizationRequest authorizationRequest = context.authorizationRequest();
    AuthorizationCode authorizationCode = createAuthorizationCode();
    User user = context.user();
    ClientId clientId = context.clientConfiguration().clientId();
    Scopes scopes = context.scopes();
    CustomProperties customProperties = context.customProperties();
    AuthorizationGranted authorizationGranted =
        new AuthorizationGranted(user, clientId, scopes, customProperties);
    AccessTokenPayload accessTokenPayload =
        createAccessTokenPayload(
            authorizationGranted, context.serverConfiguration(), context.clientConfiguration());
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
            .add(accessTokenPayload)
            .add(TokenType.Bearer)
            .add(new ExpiresIn(context.serverConfiguration().accessTokenDuration()))
            .add(accessToken);

    return authorizationResponseBuilder.build();
  }
}
