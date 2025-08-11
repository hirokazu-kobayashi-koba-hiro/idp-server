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

import org.idp.server.core.openid.oauth.OAuthRequestContext;
import org.idp.server.core.openid.oauth.OAuthRequestPattern;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfiguration;
import org.idp.server.core.openid.oauth.exception.OAuthBadRequestException;
import org.idp.server.core.openid.oauth.repository.AuthorizationRequestRepository;
import org.idp.server.core.openid.oauth.request.AuthorizationRequest;
import org.idp.server.core.openid.oauth.request.AuthorizationRequestIdentifier;
import org.idp.server.core.openid.oauth.request.OAuthRequestParameters;
import org.idp.server.core.openid.oauth.type.oauth.RequestUri;
import org.idp.server.platform.jose.JoseContext;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

/** PushedRequestUriPatternContextService */
public class PushedRequestUriPatternContextCreator implements OAuthRequestContextCreator {

  AuthorizationRequestRepository authorizationRequestRepository;

  public PushedRequestUriPatternContextCreator(
      AuthorizationRequestRepository authorizationRequestRepository) {
    this.authorizationRequestRepository = authorizationRequestRepository;
  }

  @Override
  public OAuthRequestContext create(
      Tenant tenant,
      OAuthRequestParameters parameters,
      AuthorizationServerConfiguration authorizationServerConfiguration,
      ClientConfiguration clientConfiguration) {

    OAuthRequestPattern pattern = OAuthRequestPattern.PUSHED_REQUEST_URI;
    RequestUri requestUri = parameters.requestUri();
    AuthorizationRequestIdentifier identifier =
        new AuthorizationRequestIdentifier(requestUri.extractId());
    AuthorizationRequest authorizationRequest =
        authorizationRequestRepository.find(tenant, identifier);

    if (!authorizationRequest.exists()) {
      throw new OAuthBadRequestException("invalid_request", "request uri does not exists", tenant);
    }

    return new OAuthRequestContext(
        tenant,
        pattern,
        parameters,
        new JoseContext(),
        authorizationRequest,
        authorizationServerConfiguration,
        clientConfiguration);
  }
}
