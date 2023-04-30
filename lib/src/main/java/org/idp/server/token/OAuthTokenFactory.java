package org.idp.server.token;

import java.util.UUID;
import org.idp.server.oauth.grant.AuthorizationGrant;
import org.idp.server.oauth.response.AuthorizationResponse;
import org.idp.server.oauth.token.AccessToken;
import org.idp.server.oauth.token.RefreshToken;
import org.idp.server.type.oauth.ExpiresIn;
import org.idp.server.type.oauth.TokenType;

public class OAuthTokenFactory {

  public static OAuthToken create(
      AuthorizationResponse authorizationResponse, AuthorizationGrant authorizationGrant) {
    OAuthTokenIdentifier oAuthTokenIdentifier =
        new OAuthTokenIdentifier(UUID.randomUUID().toString());
    AccessToken accessToken = authorizationResponse.accessToken();
    TokenType tokenType = authorizationResponse.tokenType();
    ExpiresIn expiresIn = authorizationResponse.expiresIn();
    TokenResponseBuilder tokenResponseBuilder =
        new TokenResponseBuilder()
            .add(accessToken.accessTokenValue())
            .add(tokenType)
            .add(expiresIn);
    TokenResponse tokenResponse = tokenResponseBuilder.build();
    return new OAuthToken(
        oAuthTokenIdentifier, tokenResponse, accessToken, new RefreshToken(), authorizationGrant);
  }
}
