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

package org.idp.server.usecases.application.enduser;

import java.util.Map;
import org.idp.server.core.oidc.identity.repository.UserQueryRepository;
import org.idp.server.core.oidc.token.TokenApi;
import org.idp.server.core.oidc.token.TokenEventPublisher;
import org.idp.server.core.oidc.token.TokenProtocol;
import org.idp.server.core.oidc.token.TokenProtocols;
import org.idp.server.core.oidc.token.handler.token.io.TokenRequest;
import org.idp.server.core.oidc.token.handler.token.io.TokenRequestResponse;
import org.idp.server.core.oidc.token.handler.tokenintrospection.io.TokenIntrospectionRequest;
import org.idp.server.core.oidc.token.handler.tokenintrospection.io.TokenIntrospectionResponse;
import org.idp.server.core.oidc.token.handler.tokenrevocation.io.TokenRevocationRequest;
import org.idp.server.core.oidc.token.handler.tokenrevocation.io.TokenRevocationResponse;
import org.idp.server.platform.datasource.Transaction;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantQueryRepository;
import org.idp.server.platform.security.type.RequestAttributes;

@Transaction
public class TokenEntryService implements TokenApi {

  TokenProtocols tokenProtocols;
  TenantQueryRepository tenantQueryRepository;
  UserQueryRepository userQueryRepository;
  TokenEventPublisher eventPublisher;

  public TokenEntryService(
      TokenProtocols tokenProtocols,
      UserQueryRepository userQueryRepository,
      TenantQueryRepository tenantQueryRepository,
      TokenEventPublisher eventPublisher) {
    this.tokenProtocols = tokenProtocols;
    this.tenantQueryRepository = tenantQueryRepository;
    this.userQueryRepository = userQueryRepository;
    this.eventPublisher = eventPublisher;
  }

  public TokenRequestResponse request(
      TenantIdentifier tenantIdentifier,
      Map<String, String[]> params,
      String authorizationHeader,
      String clientCert,
      RequestAttributes requestAttributes) {

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);
    TokenRequest tokenRequest = new TokenRequest(tenant, authorizationHeader, params);
    tokenRequest.setClientCert(clientCert);

    TokenProtocol tokenProtocol = tokenProtocols.get(tenant.authorizationProvider());

    TokenRequestResponse requestResponse = tokenProtocol.request(tokenRequest);

    if (requestResponse.isOK()) {
      eventPublisher.publish(
          tenant,
          requestResponse.oAuthToken(),
          requestResponse.securityEventType(tokenRequest),
          requestAttributes);
    }

    return requestResponse;
  }

  public TokenIntrospectionResponse inspect(
      TenantIdentifier tenantIdentifier,
      Map<String, String[]> params,
      RequestAttributes requestAttributes) {

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);
    TokenIntrospectionRequest tokenIntrospectionRequest =
        new TokenIntrospectionRequest(tenant, params);

    TokenProtocol tokenProtocol = tokenProtocols.get(tenant.authorizationProvider());

    TokenIntrospectionResponse result = tokenProtocol.inspect(tokenIntrospectionRequest);

    if (result.hasOAuthToken()) {
      eventPublisher.publish(
          tenant, result.oAuthToken(), result.securityEventType(), requestAttributes);
    }

    return result;
  }

  public TokenRevocationResponse revoke(
      TenantIdentifier tenantIdentifier,
      Map<String, String[]> request,
      String authorizationHeader,
      String clientCert,
      RequestAttributes requestAttributes) {

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);
    TokenRevocationRequest revocationRequest =
        new TokenRevocationRequest(tenant, authorizationHeader, request);
    revocationRequest.setClientCert(clientCert);

    TokenProtocol tokenProtocol = tokenProtocols.get(tenant.authorizationProvider());

    TokenRevocationResponse result = tokenProtocol.revoke(revocationRequest);

    if (result.hasOAuthToken()) {
      eventPublisher.publish(
          tenant, result.oAuthToken(), result.securityEventType(), requestAttributes);
    }

    return result;
  }
}
