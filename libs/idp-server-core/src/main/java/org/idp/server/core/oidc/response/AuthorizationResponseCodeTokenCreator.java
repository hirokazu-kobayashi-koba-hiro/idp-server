/*
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.idp.server.core.oidc.response;

import org.idp.server.core.oidc.OAuthAuthorizeContext;
import org.idp.server.core.oidc.clientauthenticator.clientcredentials.ClientCredentials;
import org.idp.server.core.oidc.grant.AuthorizationGrant;
import org.idp.server.core.oidc.request.AuthorizationRequest;
import org.idp.server.core.oidc.token.AccessToken;
import org.idp.server.core.oidc.token.AccessTokenCreator;
import org.idp.server.core.oidc.type.extension.JarmPayload;
import org.idp.server.core.oidc.type.extension.ResponseModeValue;
import org.idp.server.core.oidc.type.oauth.*;

public class AuthorizationResponseCodeTokenCreator
    implements AuthorizationResponseCreator,
        AuthorizationCodeCreatable,
        RedirectUriDecidable,
        JarmCreatable {

  AccessTokenCreator accessTokenCreator;

  public AuthorizationResponseCodeTokenCreator() {
    this.accessTokenCreator = AccessTokenCreator.getInstance();
  }

  @Override
  public AuthorizationResponse create(OAuthAuthorizeContext context) {
    AuthorizationRequest authorizationRequest = context.authorizationRequest();
    AuthorizationCode authorizationCode = createAuthorizationCode();
    AuthorizationGrant authorizationGrant = context.authorize();

    AccessToken accessToken =
        accessTokenCreator.create(
            authorizationGrant,
            context.serverConfiguration(),
            context.clientConfiguration(),
            new ClientCredentials());

    AuthorizationResponseBuilder authorizationResponseBuilder =
        new AuthorizationResponseBuilder(
                decideRedirectUri(authorizationRequest, context.clientConfiguration()),
                context.responseMode(),
                ResponseModeValue.fragment(),
                context.tokenIssuer())
            .add(authorizationCode)
            .add(TokenType.Bearer)
            .add(new ExpiresIn(context.serverConfiguration().accessTokenDuration()))
            .add(authorizationGrant.scopes())
            .add(accessToken);

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
