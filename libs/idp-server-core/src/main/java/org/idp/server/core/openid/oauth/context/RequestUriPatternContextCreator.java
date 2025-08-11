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

package org.idp.server.core.openid.oauth.context;

import java.util.Set;
import org.idp.server.core.openid.oauth.AuthorizationProfile;
import org.idp.server.core.openid.oauth.OAuthRequestContext;
import org.idp.server.core.openid.oauth.OAuthRequestPattern;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfiguration;
import org.idp.server.core.openid.oauth.exception.OAuthBadRequestException;
import org.idp.server.core.openid.oauth.factory.AuthorizationRequestFactory;
import org.idp.server.core.openid.oauth.factory.RequestObjectFactories;
import org.idp.server.core.openid.oauth.factory.RequestObjectFactoryType;
import org.idp.server.core.openid.oauth.gateway.RequestObjectGateway;
import org.idp.server.core.openid.oauth.request.AuthorizationRequest;
import org.idp.server.core.openid.oauth.request.OAuthRequestParameters;
import org.idp.server.core.openid.oauth.type.oidc.RequestObject;
import org.idp.server.platform.jose.JoseContext;
import org.idp.server.platform.jose.JoseHandler;
import org.idp.server.platform.jose.JoseInvalidException;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

/** RequestUriPatternContextService */
public class RequestUriPatternContextCreator implements OAuthRequestContextCreator {

  RequestObjectGateway requestObjectGateway;
  RequestObjectFactories requestObjectFactories;

  public RequestUriPatternContextCreator(
      RequestObjectGateway requestObjectGateway, RequestObjectFactories requestObjectFactories) {
    this.requestObjectGateway = requestObjectGateway;
    this.requestObjectFactories = requestObjectFactories;
  }

  @Override
  public OAuthRequestContext create(
      Tenant tenant,
      OAuthRequestParameters parameters,
      AuthorizationServerConfiguration authorizationServerConfiguration,
      ClientConfiguration clientConfiguration) {
    try {

      if (!clientConfiguration.isRegisteredRequestUri(parameters.requestUri().value())) {
        throw new OAuthBadRequestException(
            "invalid_request",
            String.format("request uri does not registered (%s)", parameters.requestUri().value()),
            tenant);
      }

      RequestObject requestObject = requestObjectGateway.get(parameters.requestUri());
      JoseHandler joseHandler = new JoseHandler();
      JoseContext joseContext =
          joseHandler.handle(
              requestObject.value(),
              clientConfiguration.jwks(),
              authorizationServerConfiguration.jwks(),
              clientConfiguration.clientSecretValue());
      joseContext.verifySignature();

      OAuthRequestPattern pattern = OAuthRequestPattern.REQUEST_URI;
      Set<String> filteredScopes =
          filterScopes(pattern, parameters, joseContext, clientConfiguration);

      AuthorizationProfile profile = analyze(filteredScopes, authorizationServerConfiguration);

      RequestObjectFactoryType requestObjectFactoryType =
          selectRequestObjectType(profile, authorizationServerConfiguration, clientConfiguration);
      AuthorizationRequestFactory requestFactory =
          requestObjectFactories.get(requestObjectFactoryType);
      AuthorizationRequest authorizationRequest =
          requestFactory.create(
              tenant,
              profile,
              parameters,
              joseContext,
              filteredScopes,
              authorizationServerConfiguration,
              clientConfiguration);

      return new OAuthRequestContext(
          tenant,
          pattern,
          parameters,
          joseContext,
          authorizationRequest,
          authorizationServerConfiguration,
          clientConfiguration);
    } catch (JoseInvalidException exception) {
      throw new OAuthBadRequestException(
          "invalid_request", exception.getMessage(), exception, tenant);
    }
  }
}
