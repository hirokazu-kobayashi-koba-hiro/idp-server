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
import org.idp.server.control_plane.management.audit.AuditLogManagementApi;
import org.idp.server.control_plane.management.audit.handler.*;
import org.idp.server.control_plane.management.audit.io.AuditLogFindListRequest;
import org.idp.server.control_plane.management.audit.io.AuditLogFindRequest;
import org.idp.server.control_plane.management.audit.io.AuditLogManagementResponse;
import org.idp.server.platform.audit.*;
import org.idp.server.platform.audit.AuditLogIdentifier;
import org.idp.server.platform.audit.AuditLogQueries;
import org.idp.server.platform.audit.AuditLogQueryRepository;
import org.idp.server.platform.datasource.Transaction;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantQueryRepository;
import org.idp.server.platform.type.RequestAttributes;

@Transaction
public class AuditLogManagementEntryService implements AuditLogManagementApi {

  private final AuditLogManagementHandler handler;
  private final AuditLogPublisher auditLogPublisher;

  public AuditLogManagementEntryService(
      AuditLogQueryRepository auditLogQueryRepository,
      TenantQueryRepository tenantQueryRepository,
      AuditLogPublisher auditLogPublisher) {

    Map<String, AuditLogManagementService<?>> services = new HashMap<>();
    services.put("findList", new AuditLogFindListService(auditLogQueryRepository));
    services.put("get", new AuditLogFindService(auditLogQueryRepository));

    this.handler = new AuditLogManagementHandler(services, this, tenantQueryRepository);
    this.auditLogPublisher = auditLogPublisher;
  }

  @Override
  @Transaction(readOnly = true)
  public AuditLogManagementResponse findList(
      AdminAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      AuditLogQueries queries,
      RequestAttributes requestAttributes) {

    AuditLogFindListRequest findListRequest = new AuditLogFindListRequest(queries);
    AuditLogManagementResult result =
        handler.handle(
            "findList",
            authenticationContext,
            tenantIdentifier,
            findListRequest,
            requestAttributes);

    AuditLog auditLog = AuditLogCreator.create(result.context());
    auditLogPublisher.publish(auditLog);

    return result.toResponse();
  }

  @Override
  @Transaction(readOnly = true)
  public AuditLogManagementResponse get(
      AdminAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      AuditLogIdentifier identifier,
      RequestAttributes requestAttributes) {

    AuditLogFindRequest findRequest = new AuditLogFindRequest(identifier);
    AuditLogManagementResult result =
        handler.handle(
            "get", authenticationContext, tenantIdentifier, findRequest, requestAttributes);

    AuditLog auditLog = AuditLogCreator.create(result.context());
    auditLogPublisher.publish(auditLog);

    return result.toResponse();
  }
}
