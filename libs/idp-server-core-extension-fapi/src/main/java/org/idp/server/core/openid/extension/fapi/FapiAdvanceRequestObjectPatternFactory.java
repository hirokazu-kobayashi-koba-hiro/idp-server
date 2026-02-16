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

package org.idp.server.core.openid.extension.fapi;

import java.util.Set;
import org.idp.server.core.openid.oauth.AuthorizationProfile;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfiguration;
import org.idp.server.core.openid.oauth.factory.AuthorizationRequestObjectFactory;
import org.idp.server.core.openid.oauth.factory.RequestObjectFactoryType;
import org.idp.server.core.openid.oauth.request.AuthorizationRequest;
import org.idp.server.core.openid.oauth.request.AuthorizationRequestBuilder;
import org.idp.server.core.openid.oauth.request.OAuthRequestParameters;
import org.idp.server.core.openid.oauth.request.RequestObjectParameters;
import org.idp.server.core.openid.oauth.type.extension.ExpiresAt;
import org.idp.server.core.openid.oauth.type.oauth.*;
import org.idp.server.core.openid.oauth.type.oidc.*;
import org.idp.server.core.openid.oauth.type.pkce.CodeChallenge;
import org.idp.server.core.openid.oauth.type.pkce.CodeChallengeMethod;
import org.idp.server.core.openid.oauth.type.rar.AuthorizationDetailsEntity;
import org.idp.server.platform.date.SystemDateTime;
import org.idp.server.platform.jose.JoseContext;
import org.idp.server.platform.jose.JsonWebTokenClaims;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

/**
 * shall only use the parameters included in the signed request object passed via the request or
 * request_uri parameter;
 */
public class FapiAdvanceRequestObjectPatternFactory implements AuthorizationRequestObjectFactory {

  @Override
  public RequestObjectFactoryType type() {
    return RequestObjectFactoryType.FAPI;
  }

  @Override
  public AuthorizationRequest create(
      Tenant tenant,
      AuthorizationProfile profile,
      OAuthRequestParameters parameters,
      JoseContext joseContext,
      Set<String> filteredScopes,
      AuthorizationServerConfiguration authorizationServerConfiguration,
      ClientConfiguration clientConfiguration,
      boolean isPushed) {

    JsonWebTokenClaims jsonWebTokenClaims = joseContext.claims();
    RequestObjectParameters requestObjectParameters =
        new RequestObjectParameters(jsonWebTokenClaims.payload());
    Scopes scopes = new Scopes(filteredScopes);
    ResponseType responseType = requestObjectParameters.responseType();
    RequestedClientId requestedClientId = requestObjectParameters.clientId();
    RedirectUri redirectUri = requestObjectParameters.redirectUri();
    State state = requestObjectParameters.state();
    ResponseMode responseMode = requestObjectParameters.responseMode();
    Nonce nonce = requestObjectParameters.nonce();
    Display display = requestObjectParameters.display();
    Prompts prompts = requestObjectParameters.prompts();
    MaxAge maxAge = requestObjectParameters.maxAge();
    UiLocales uiLocales = requestObjectParameters.uiLocales();
    IdTokenHint idTokenHint = requestObjectParameters.idTokenHint();
    LoginHint loginHint = requestObjectParameters.loginHint();
    AcrValues acrValues = requestObjectParameters.acrValues();
    ClaimsValue claimsValue = requestObjectParameters.claims();
    RequestObject requestObject = parameters.request();
    RequestUri requestUri = parameters.requestUri();
    CodeChallenge codeChallenge = requestObjectParameters.codeChallenge();
    CodeChallengeMethod codeChallengeMethod = requestObjectParameters.codeChallengeMethod();
    AuthorizationDetailsEntity authorizationDetailsEntity =
        requestObjectParameters.authorizationDetailsEntity();

    AuthorizationRequestBuilder builder = new AuthorizationRequestBuilder();
    builder.add(createIdentifier());
    builder.add(tenant.identifier());
    builder.add(profile);
    builder.add(scopes);
    builder.add(responseType);
    builder.add(requestedClientId);
    builder.add(clientConfiguration.clientAttributes());
    builder.add(redirectUri);
    builder.add(state);
    builder.add(responseMode);
    builder.add(nonce);
    builder.add(display);
    builder.add(prompts);
    if (maxAge.exists()) {
      builder.add(maxAge);
    } else {
      builder.add(new MaxAge(authorizationServerConfiguration.defaultMaxAge()));
    }
    builder.add(uiLocales);
    builder.add(idTokenHint);
    builder.add(loginHint);
    builder.add(acrValues);
    builder.add(claimsValue);
    builder.add(requestObject);
    builder.add(requestUri);
    builder.add(convertClaimsPayload(claimsValue));
    builder.add(codeChallenge);
    builder.add(codeChallengeMethod);
    builder.add(convertAuthorizationDetails(authorizationDetailsEntity));
    builder.add(parameters.customParams());
    int expiresIn =
        isPushed
            ? authorizationServerConfiguration.pushedAuthorizationRequestExpiresIn()
            : authorizationServerConfiguration.oauthAuthorizationRequestExpiresIn();
    builder.add(new ExpiresIn(expiresIn));
    builder.add(new ExpiresAt(SystemDateTime.now().plusSeconds(expiresIn)));
    return builder.build();
  }
}
