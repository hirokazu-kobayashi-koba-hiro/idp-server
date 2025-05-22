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


package org.idp.server.core.oidc.context;

import java.util.Set;
import org.idp.server.basic.jose.JoseContext;
import org.idp.server.basic.jose.JoseHandler;
import org.idp.server.basic.jose.JoseInvalidException;
import org.idp.server.core.oidc.AuthorizationProfile;
import org.idp.server.core.oidc.OAuthRequestContext;
import org.idp.server.core.oidc.OAuthRequestPattern;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.oidc.configuration.client.ClientConfiguration;
import org.idp.server.core.oidc.exception.OAuthBadRequestException;
import org.idp.server.core.oidc.factory.AuthorizationRequestFactory;
import org.idp.server.core.oidc.factory.RequestObjectFactories;
import org.idp.server.core.oidc.factory.RequestObjectFactoryType;
import org.idp.server.core.oidc.request.AuthorizationRequest;
import org.idp.server.core.oidc.request.OAuthRequestParameters;
import org.idp.server.core.oidc.validator.RequestObjectValidator;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

/** RequestObjectPatternContextService */
public class RequestObjectPatternContextCreator implements OAuthRequestContextCreator {

  RequestObjectFactories requestObjectFactories;

  public RequestObjectPatternContextCreator(RequestObjectFactories requestObjectFactories) {
    this.requestObjectFactories = requestObjectFactories;
  }

  @Override
  public OAuthRequestContext create(
      Tenant tenant,
      OAuthRequestParameters parameters,
      AuthorizationServerConfiguration authorizationServerConfiguration,
      ClientConfiguration clientConfiguration) {
    try {
      RequestObjectValidator validator =
          new RequestObjectValidator(
              parameters, authorizationServerConfiguration, clientConfiguration);
      validator.validate();

      JoseHandler joseHandler = new JoseHandler();
      JoseContext joseContext =
          joseHandler.handle(
              parameters.request().value(),
              clientConfiguration.jwks(),
              authorizationServerConfiguration.jwks(),
              clientConfiguration.clientSecretValue());
      joseContext.verifySignature();

      OAuthRequestPattern pattern = OAuthRequestPattern.REQUEST_OBJECT;
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
