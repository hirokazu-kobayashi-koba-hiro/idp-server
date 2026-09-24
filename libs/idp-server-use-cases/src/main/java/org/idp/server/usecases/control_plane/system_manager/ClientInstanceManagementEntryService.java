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

package org.idp.server.usecases.control_plane.system_manager;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.control_plane.base.AdminAuthenticationContext;
import org.idp.server.control_plane.base.AuditLogCreator;
import org.idp.server.control_plane.management.oidc.clientinstance.ClientInstanceManagementApi;
import org.idp.server.control_plane.management.oidc.clientinstance.handler.ClientInstanceDeletionService;
import org.idp.server.control_plane.management.oidc.clientinstance.handler.ClientInstanceFindListService;
import org.idp.server.control_plane.management.oidc.clientinstance.handler.ClientInstanceFindService;
import org.idp.server.control_plane.management.oidc.clientinstance.handler.ClientInstanceManagementHandler;
import org.idp.server.control_plane.management.oidc.clientinstance.handler.ClientInstanceManagementService;
import org.idp.server.control_plane.management.oidc.clientinstance.handler.ClientInstanceRegistrationService;
import org.idp.server.control_plane.management.oidc.clientinstance.handler.ClientInstanceRevocationService;
import org.idp.server.control_plane.management.oidc.clientinstance.io.*;
import org.idp.server.core.openid.clientinstance.ClientInstanceCommandRepository;
import org.idp.server.core.openid.clientinstance.ClientInstanceIdentifier;
import org.idp.server.core.openid.clientinstance.ClientInstanceQueries;
import org.idp.server.core.openid.clientinstance.ClientInstanceQueryRepository;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfigurationQueryRepository;
import org.idp.server.core.openid.token.repository.OAuthTokenCommandRepository;
import org.idp.server.platform.audit.AuditLog;
import org.idp.server.platform.audit.AuditLogPublisher;
import org.idp.server.platform.datasource.Transaction;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantQueryRepository;
import org.idp.server.platform.type.RequestAttributes;

@Transaction
public class ClientInstanceManagementEntryService implements ClientInstanceManagementApi {

  private final ClientInstanceManagementHandler handler;
  private final AuditLogPublisher auditLogPublisher;

  public ClientInstanceManagementEntryService(
      TenantQueryRepository tenantQueryRepository,
      ClientInstanceCommandRepository clientInstanceCommandRepository,
      ClientInstanceQueryRepository clientInstanceQueryRepository,
      ClientConfigurationQueryRepository clientConfigurationQueryRepository,
      OAuthTokenCommandRepository oAuthTokenCommandRepository,
      AuditLogPublisher auditLogPublisher) {

    Map<String, ClientInstanceManagementService<?>> services = new HashMap<>();
    services.put(
        "create",
        new ClientInstanceRegistrationService(
            clientInstanceQueryRepository,
            clientInstanceCommandRepository,
            clientConfigurationQueryRepository));
    services.put("findList", new ClientInstanceFindListService(clientInstanceQueryRepository));
    services.put("get", new ClientInstanceFindService(clientInstanceQueryRepository));
    services.put(
        "revoke",
        new ClientInstanceRevocationService(
            clientInstanceQueryRepository,
            clientInstanceCommandRepository,
            oAuthTokenCommandRepository));
    services.put(
        "delete",
        new ClientInstanceDeletionService(
            clientInstanceQueryRepository,
            clientInstanceCommandRepository,
            oAuthTokenCommandRepository));

    this.handler = new ClientInstanceManagementHandler(services, this, tenantQueryRepository);
    this.auditLogPublisher = auditLogPublisher;
  }

  @Override
  public ClientInstanceManagementResponse create(
      AdminAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      ClientInstanceRegistrationRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    return handle(
        "create", authenticationContext, tenantIdentifier, request, requestAttributes, dryRun);
  }

  @Override
  @Transaction(readOnly = true)
  public ClientInstanceManagementResponse findList(
      AdminAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      ClientInstanceQueries queries,
      RequestAttributes requestAttributes) {

    return handle(
        "findList",
        authenticationContext,
        tenantIdentifier,
        new ClientInstanceFindListRequest(queries),
        requestAttributes,
        false);
  }

  @Override
  @Transaction(readOnly = true)
  public ClientInstanceManagementResponse get(
      AdminAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      ClientInstanceIdentifier identifier,
      RequestAttributes requestAttributes) {

    return handle(
        "get",
        authenticationContext,
        tenantIdentifier,
        new ClientInstanceFindRequest(identifier),
        requestAttributes,
        false);
  }

  @Override
  public ClientInstanceManagementResponse revoke(
      AdminAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      ClientInstanceIdentifier identifier,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    return handle(
        "revoke",
        authenticationContext,
        tenantIdentifier,
        new ClientInstanceFindRequest(identifier),
        requestAttributes,
        dryRun);
  }

  @Override
  public ClientInstanceManagementResponse delete(
      AdminAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      ClientInstanceIdentifier identifier,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    return handle(
        "delete",
        authenticationContext,
        tenantIdentifier,
        new ClientInstanceFindRequest(identifier),
        requestAttributes,
        dryRun);
  }

  private ClientInstanceManagementResponse handle(
      String method,
      AdminAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      ClientInstanceManagementRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    ClientInstanceManagementResult result =
        handler.handle(
            method, authenticationContext, tenantIdentifier, request, requestAttributes, dryRun);

    AuditLog auditLog = AuditLogCreator.create(result.context());
    auditLogPublisher.publish(auditLog);

    return result.toResponse(dryRun);
  }
}
