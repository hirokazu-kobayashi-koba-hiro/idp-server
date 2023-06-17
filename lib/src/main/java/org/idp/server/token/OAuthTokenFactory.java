package org.idp.server.token;

import java.util.UUID;
import org.idp.server.oauth.grant.AuthorizationGrant;
import org.idp.server.oauth.response.AuthorizationResponse;
import org.idp.server.oauth.token.AccessToken;

public class OAuthTokenFactory {

  public static OAuthToken create(
      AuthorizationResponse authorizationResponse, AuthorizationGrant authorizationGrant) {
    OAuthTokenIdentifier oAuthTokenIdentifier =
        new OAuthTokenIdentifier(UUID.randomUUID().toString());
    AccessToken accessToken = authorizationResponse.accessToken();

    return new OAuthTokenBuilder(oAuthTokenIdentifier).add(accessToken).build();
  }
}
