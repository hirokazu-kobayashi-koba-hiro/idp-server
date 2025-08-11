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

package org.idp.server.core.openid.oauth.response;

import org.idp.server.core.openid.grant_management.grant.AuthorizationGrant;
import org.idp.server.core.openid.identity.id_token.IdTokenCreator;
import org.idp.server.core.openid.identity.id_token.IdTokenCustomClaims;
import org.idp.server.core.openid.identity.id_token.IdTokenCustomClaimsBuilder;
import org.idp.server.core.openid.oauth.OAuthAuthorizeContext;
import org.idp.server.core.openid.oauth.clientauthenticator.clientcredentials.ClientCredentials;
import org.idp.server.core.openid.oauth.request.AuthorizationRequest;
import org.idp.server.core.openid.oauth.type.extension.JarmPayload;
import org.idp.server.core.openid.oauth.type.extension.ResponseModeValue;
import org.idp.server.core.openid.oauth.type.oauth.ExpiresIn;
import org.idp.server.core.openid.oauth.type.oauth.TokenType;
import org.idp.server.core.openid.oauth.type.oidc.IdToken;
import org.idp.server.core.openid.token.AccessToken;
import org.idp.server.core.openid.token.AccessTokenCreator;

public class AuthorizationResponseTokenIdTokenCreator
    implements AuthorizationResponseCreator, RedirectUriDecidable, JarmCreatable {

  IdTokenCreator idTokenCreator;
  AccessTokenCreator accessTokenCreator;

  public AuthorizationResponseTokenIdTokenCreator() {
    this.idTokenCreator = IdTokenCreator.getInstance();
    this.accessTokenCreator = AccessTokenCreator.getInstance();
  }

  @Override
  public AuthorizationResponse create(OAuthAuthorizeContext context) {
    AuthorizationRequest authorizationRequest = context.authorizationRequest();
    AuthorizationGrant authorizationGrant = context.authorize();

    AccessToken accessToken =
        accessTokenCreator.create(
            authorizationGrant,
            context.serverConfiguration(),
            context.clientConfiguration(),
            new ClientCredentials());

    IdTokenCustomClaims idTokenCustomClaims =
        new IdTokenCustomClaimsBuilder()
            .add(authorizationRequest.state())
            .add(authorizationRequest.nonce())
            .add(accessToken.accessTokenEntity())
            .build();

    IdToken idToken =
        idTokenCreator.createIdToken(
            context.user(),
            context.authentication(),
            context.authorize(),
            idTokenCustomClaims,
            context.requestedClaimsPayload(),
            context.serverConfiguration(),
            context.clientConfiguration());

    AuthorizationResponseBuilder authorizationResponseBuilder =
        new AuthorizationResponseBuilder(
                decideRedirectUri(authorizationRequest, context.clientConfiguration()),
                context.responseMode(),
                ResponseModeValue.fragment(),
                context.tokenIssuer())
            .add(TokenType.Bearer)
            .add(new ExpiresIn(context.serverConfiguration().accessTokenDuration()))
            .add(accessToken)
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
