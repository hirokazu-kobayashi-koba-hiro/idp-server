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
import org.idp.server.control_plane.management.oidc.clientinstance.io.*;
import org.idp.server.core.openid.clientinstance.ClientInstanceCommandRepository;
import org.idp.server.core.openid.clientinstance.ClientInstanceIdentifier;
import org.idp.server.core.openid.clientinstance.ClientInstanceQueryRepository;
import org.idp.server.core.openid.oauth.type.oauth.RequestedClientId;
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
      AuditLogPublisher auditLogPublisher) {

    Map<String, ClientInstanceManagementService<?>> services = new HashMap<>();
    services.put("create", new ClientInstanceRegistrationService(clientInstanceCommandRepository));
    services.put("findList", new ClientInstanceFindListService(clientInstanceQueryRepository));
    services.put("get", new ClientInstanceFindService(clientInstanceQueryRepository));
    services.put(
        "delete",
        new ClientInstanceDeletionService(
            clientInstanceQueryRepository, clientInstanceCommandRepository));

    this.handler = new ClientInstanceManagementHandler(services, this, tenantQueryRepository);
    this.auditLogPublisher = auditLogPublisher;
  }

  @Override
  public ClientInstanceManagementResponse create(
      AdminAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      RequestedClientId requestedClientId,
      ClientInstanceRegistrationRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    ClientInstanceManagementResult result =
        handler.handle(
            "create", authenticationContext, tenantIdentifier, request, requestAttributes, dryRun);

    AuditLog auditLog = AuditLogCreator.create(result.context());
    auditLogPublisher.publish(auditLog);

    return result.toResponse(dryRun);
  }

  @Override
  @Transaction(readOnly = true)
  public ClientInstanceManagementResponse findList(
      AdminAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      RequestedClientId requestedClientId,
      int limit,
      int offset,
      RequestAttributes requestAttributes) {

    ClientInstanceFindListRequest request =
        new ClientInstanceFindListRequest(requestedClientId, limit, offset);
    ClientInstanceManagementResult result =
        handler.handle(
            "findList", authenticationContext, tenantIdentifier, request, requestAttributes, false);

    AuditLog auditLog = AuditLogCreator.create(result.context());
    auditLogPublisher.publish(auditLog);

    return result.toResponse(false);
  }

  @Override
  @Transaction(readOnly = true)
  public ClientInstanceManagementResponse get(
      AdminAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      RequestedClientId requestedClientId,
      ClientInstanceIdentifier identifier,
      RequestAttributes requestAttributes) {

    ClientInstanceFindRequest request =
        new ClientInstanceFindRequest(requestedClientId, identifier);
    ClientInstanceManagementResult result =
        handler.handle(
            "get", authenticationContext, tenantIdentifier, request, requestAttributes, false);

    AuditLog auditLog = AuditLogCreator.create(result.context());
    auditLogPublisher.publish(auditLog);

    return result.toResponse(false);
  }

  @Override
  public ClientInstanceManagementResponse delete(
      AdminAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      RequestedClientId requestedClientId,
      ClientInstanceIdentifier identifier,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    ClientInstanceFindRequest request =
        new ClientInstanceFindRequest(requestedClientId, identifier);
    ClientInstanceManagementResult result =
        handler.handle(
            "delete", authenticationContext, tenantIdentifier, request, requestAttributes, dryRun);

    AuditLog auditLog = AuditLogCreator.create(result.context());
    auditLogPublisher.publish(auditLog);

    return result.toResponse(dryRun);
  }
}
