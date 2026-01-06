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

import org.idp.server.core.openid.identity.id_token.IdTokenCreator;
import org.idp.server.core.openid.identity.id_token.IdTokenCustomClaims;
import org.idp.server.core.openid.identity.id_token.IdTokenCustomClaimsBuilder;
import org.idp.server.core.openid.oauth.OAuthAuthorizeContext;
import org.idp.server.core.openid.oauth.request.AuthorizationRequest;
import org.idp.server.core.openid.oauth.type.extension.CustomProperties;
import org.idp.server.core.openid.oauth.type.extension.JarmPayload;
import org.idp.server.core.openid.oauth.type.extension.ResponseModeValue;
import org.idp.server.core.openid.oauth.type.oidc.IdToken;
import org.idp.server.core.openid.session.ClientSessionIdentifier;

public class AuthorizationResponseIdTokenCreator
    implements AuthorizationResponseCreator, RedirectUriDecidable, JarmCreatable {

  IdTokenCreator idTokenCreator;

  public AuthorizationResponseIdTokenCreator() {
    this.idTokenCreator = IdTokenCreator.getInstance();
  }

  @Override
  public AuthorizationResponse create(OAuthAuthorizeContext context) {
    AuthorizationRequest authorizationRequest = context.authorizationRequest();
    IdTokenCustomClaimsBuilder idTokenCustomClaimsBuilder =
        new IdTokenCustomClaimsBuilder()
            .add(authorizationRequest.state())
            .add(authorizationRequest.nonce());

    // Add sid for OIDC Session Management if present
    CustomProperties customProperties = context.customProperties();
    if (customProperties != null && customProperties.contains("sid")) {
      String sidValue = customProperties.getValueAsStringOrEmpty("sid");
      if (!sidValue.isEmpty()) {
        idTokenCustomClaimsBuilder.add(new ClientSessionIdentifier(sidValue));
      }
    }

    IdTokenCustomClaims idTokenCustomClaims = idTokenCustomClaimsBuilder.build();
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
