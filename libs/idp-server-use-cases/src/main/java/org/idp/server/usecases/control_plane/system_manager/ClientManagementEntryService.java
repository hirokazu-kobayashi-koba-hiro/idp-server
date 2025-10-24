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
import org.idp.server.control_plane.management.oidc.client.ClientManagementApi;
import org.idp.server.control_plane.management.oidc.client.handler.ClientCreationService;
import org.idp.server.control_plane.management.oidc.client.handler.ClientDeletionService;
import org.idp.server.control_plane.management.oidc.client.handler.ClientFindListService;
import org.idp.server.control_plane.management.oidc.client.handler.ClientFindService;
import org.idp.server.control_plane.management.oidc.client.handler.ClientManagementHandler;
import org.idp.server.control_plane.management.oidc.client.handler.ClientManagementService;
import org.idp.server.control_plane.management.oidc.client.handler.ClientUpdateService;
import org.idp.server.control_plane.management.oidc.client.io.*;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfigurationCommandRepository;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfigurationQueryRepository;
import org.idp.server.core.openid.oauth.configuration.client.ClientIdentifier;
import org.idp.server.core.openid.oauth.configuration.client.ClientQueries;
import org.idp.server.platform.audit.AuditLog;
import org.idp.server.platform.audit.AuditLogPublisher;
import org.idp.server.platform.datasource.Transaction;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantQueryRepository;
import org.idp.server.platform.type.RequestAttributes;

@Transaction
public class ClientManagementEntryService implements ClientManagementApi {

  private final ClientManagementHandler handler;
  private final AuditLogPublisher auditLogPublisher;

  public ClientManagementEntryService(
      TenantQueryRepository tenantQueryRepository,
      ClientConfigurationCommandRepository clientConfigurationCommandRepository,
      ClientConfigurationQueryRepository clientConfigurationQueryRepository,
      AuditLogPublisher auditLogPublisher) {

    Map<String, ClientManagementService<?>> services = new HashMap<>();
    services.put("create", new ClientCreationService(clientConfigurationCommandRepository));
    services.put("findList", new ClientFindListService(clientConfigurationQueryRepository));
    services.put("get", new ClientFindService(clientConfigurationQueryRepository));
    services.put(
        "update",
        new ClientUpdateService(
            clientConfigurationQueryRepository, clientConfigurationCommandRepository));
    services.put(
        "delete",
        new ClientDeletionService(
            clientConfigurationQueryRepository, clientConfigurationCommandRepository));

    this.handler = new ClientManagementHandler(services, this, tenantQueryRepository);
    this.auditLogPublisher = auditLogPublisher;
  }

  @Override
  public ClientManagementResponse create(
      AdminAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      ClientRegistrationRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    ClientManagementResult result =
        handler.handle(
            "create", authenticationContext, tenantIdentifier, request, requestAttributes, dryRun);

    AuditLog auditLog = AuditLogCreator.create(result.context());
    auditLogPublisher.publish(auditLog);

    return result.toResponse(dryRun);
  }

  @Override
  @Transaction(readOnly = true)
  public ClientManagementResponse findList(
      AdminAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      ClientQueries queries,
      RequestAttributes requestAttributes) {

    ClientFindListRequest findListRequest = new ClientFindListRequest(queries);
    ClientManagementResult result =
        handler.handle(
            "findList",
            authenticationContext,
            tenantIdentifier,
            findListRequest,
            requestAttributes,
            false);

    AuditLog auditLog = AuditLogCreator.create(result.context());
    auditLogPublisher.publish(auditLog);

    return result.toResponse(false);
  }

  @Override
  @Transaction(readOnly = true)
  public ClientManagementResponse get(
      AdminAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      ClientIdentifier clientIdentifier,
      RequestAttributes requestAttributes) {

    ClientFindRequest findRequest = new ClientFindRequest(clientIdentifier);
    ClientManagementResult result =
        handler.handle(
            "get", authenticationContext, tenantIdentifier, findRequest, requestAttributes, false);

    AuditLog auditLog = AuditLogCreator.create(result.context());
    auditLogPublisher.publish(auditLog);

    return result.toResponse(false);
  }

  @Override
  public ClientManagementResponse update(
      AdminAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      ClientIdentifier clientIdentifier,
      ClientRegistrationRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    ClientUpdateRequest updateRequest = new ClientUpdateRequest(clientIdentifier, request);
    ClientManagementResult result =
        handler.handle(
            "update",
            authenticationContext,
            tenantIdentifier,
            updateRequest,
            requestAttributes,
            dryRun);

    AuditLog auditLog = AuditLogCreator.create(result.context());
    auditLogPublisher.publish(auditLog);

    return result.toResponse(dryRun);
  }

  @Override
  public ClientManagementResponse delete(
      AdminAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      ClientIdentifier clientIdentifier,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    ClientDeleteRequest deleteRequest = new ClientDeleteRequest(clientIdentifier);
    ClientManagementResult result =
        handler.handle(
            "delete",
            authenticationContext,
            tenantIdentifier,
            deleteRequest,
            requestAttributes,
            dryRun);

    AuditLog auditLog = AuditLogCreator.create(result.context());
    auditLogPublisher.publish(auditLog);

    return result.toResponse(dryRun);
  }
}
