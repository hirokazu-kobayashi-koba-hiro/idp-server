package org.idp.server.core.token;

import java.util.UUID;
import org.idp.server.core.oidc.grant.AuthorizationGrant;
import org.idp.server.core.oidc.response.AuthorizationResponse;
import org.idp.server.core.oidc.token.AccessToken;

public class OAuthTokenFactory {

  public static OAuthToken create(AuthorizationResponse authorizationResponse, AuthorizationGrant authorizationGrant) {
    OAuthTokenIdentifier oAuthTokenIdentifier = new OAuthTokenIdentifier(UUID.randomUUID().toString());
    AccessToken accessToken = authorizationResponse.accessToken();

    return new OAuthTokenBuilder(oAuthTokenIdentifier).add(accessToken).build();
  }
}
