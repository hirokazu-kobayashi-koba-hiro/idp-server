/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.oidc.token;

import java.util.UUID;
import org.idp.server.core.oidc.grant.AuthorizationGrant;
import org.idp.server.core.oidc.response.AuthorizationResponse;

public class OAuthTokenFactory {

  public static OAuthToken create(
      AuthorizationResponse authorizationResponse, AuthorizationGrant authorizationGrant) {
    OAuthTokenIdentifier oAuthTokenIdentifier =
        new OAuthTokenIdentifier(UUID.randomUUID().toString());
    AccessToken accessToken = authorizationResponse.accessToken();

    return new OAuthTokenBuilder(oAuthTokenIdentifier).add(accessToken).build();
  }
}
