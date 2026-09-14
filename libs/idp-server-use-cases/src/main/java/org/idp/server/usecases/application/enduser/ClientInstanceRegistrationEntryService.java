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

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.openid.clientinstance.registration.ClientInstanceRegistrationApi;
import org.idp.server.core.openid.clientinstance.registration.ClientInstanceRegistrationEventPublisher;
import org.idp.server.core.openid.clientinstance.registration.ClientInstanceRegistrationProtocol;
import org.idp.server.core.openid.clientinstance.registration.ClientInstanceRegistrationProtocols;
import org.idp.server.core.openid.clientinstance.registration.handler.io.ClientInstanceChallengeRequest;
import org.idp.server.core.openid.clientinstance.registration.handler.io.ClientInstanceRegisterRequest;
import org.idp.server.core.openid.clientinstance.registration.handler.io.ClientInstanceRegistrationResponse;
import org.idp.server.platform.datasource.Transaction;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantQueryRepository;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Client Instance registration for end-user applications.
 *
 * <p>Both endpoints are unauthenticated: the challenge is an authorization ticket decided by the
 * server, and the platform attestation bound to it is what authenticates the registration. Those
 * decisions belong to the protocol; this service resolves the tenant, hands the request over, and
 * records the outcome as a security event.
 */
@Transaction
public class ClientInstanceRegistrationEntryService implements ClientInstanceRegistrationApi {

  TenantQueryRepository tenantQueryRepository;
  ClientInstanceRegistrationProtocols protocols;
  ClientInstanceRegistrationEventPublisher eventPublisher;

  public ClientInstanceRegistrationEntryService(
      TenantQueryRepository tenantQueryRepository,
      ClientInstanceRegistrationProtocols protocols,
      ClientInstanceRegistrationEventPublisher eventPublisher) {
    this.tenantQueryRepository = tenantQueryRepository;
    this.protocols = protocols;
    this.eventPublisher = eventPublisher;
  }

  @Override
  public ClientInstanceRegistrationResponse challenge(
      TenantIdentifier tenantIdentifier,
      ClientInstanceChallengeRequest request,
      RequestAttributes requestAttributes) {

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);
    ClientInstanceRegistrationProtocol protocol = protocols.get(tenant.authorizationProvider());

    ClientInstanceRegistrationResponse response = protocol.challenge(tenant, request);

    // The challenge endpoint knows who asked, even when it refuses to say why it declined.
    Map<String, Object> details = new HashMap<>();
    details.put("client_id", request.requestedClientId().value());
    if (request.deviceId() != null) {
      details.put("device_id", request.deviceId());
    }

    if (response.isError()) {
      eventPublisher.publishFailure(
          tenant, "registration challenge", response.auditReason(), details, requestAttributes);
      return response;
    }

    eventPublisher.publishChallengeIssued(
        tenant,
        response.requestedClientId(),
        request.deviceId(),
        response.instanceIdentifier(),
        requestAttributes);

    return response;
  }

  @Override
  public ClientInstanceRegistrationResponse register(
      TenantIdentifier tenantIdentifier,
      ClientInstanceRegisterRequest request,
      RequestAttributes requestAttributes) {

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);
    ClientInstanceRegistrationProtocol protocol = protocols.get(tenant.authorizationProvider());

    ClientInstanceRegistrationResponse response = protocol.register(tenant, request);

    if (response.isError()) {
      // Only the ticket identifies this request, and a rejected ticket may be unknown, so the
      // client and the device are not always resolvable here. The issuance event holds them.
      eventPublisher.publishFailure(
          tenant, "registration", response.auditReason(), Map.of(), requestAttributes);
      return response;
    }

    eventPublisher.publishSuccess(
        tenant, response.requestedClientId(), response.instanceIdentifier(), requestAttributes);

    return response;
  }
}
