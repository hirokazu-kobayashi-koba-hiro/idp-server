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

import java.util.Map;
import org.idp.server.control_plane.base.AdminAuthenticationContext;
import org.idp.server.control_plane.base.AuditLogCreator;
import org.idp.server.control_plane.management.security.event.SecurityEventManagementApi;
import org.idp.server.control_plane.management.security.event.handler.SecurityEventFindListService;
import org.idp.server.control_plane.management.security.event.handler.SecurityEventFindService;
import org.idp.server.control_plane.management.security.event.handler.SecurityEventManagementHandler;
import org.idp.server.control_plane.management.security.event.handler.SecurityEventManagementResult;
import org.idp.server.control_plane.management.security.event.handler.SecurityEventManagementService;
import org.idp.server.control_plane.management.security.event.io.SecurityEventManagementFindListRequest;
import org.idp.server.control_plane.management.security.event.io.SecurityEventManagementFindRequest;
import org.idp.server.control_plane.management.security.event.io.SecurityEventManagementResponse;
import org.idp.server.platform.audit.AuditLog;
import org.idp.server.platform.audit.AuditLogPublisher;
import org.idp.server.platform.datasource.Transaction;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantQueryRepository;
import org.idp.server.platform.security.SecurityEventQueries;
import org.idp.server.platform.security.event.SecurityEventIdentifier;
import org.idp.server.platform.security.repository.SecurityEventQueryRepository;
import org.idp.server.platform.type.RequestAttributes;

@Transaction
public class SecurityEventManagementEntryService implements SecurityEventManagementApi {

  private final SecurityEventManagementHandler handler;
  private final AuditLogPublisher auditLogPublisher;

  public SecurityEventManagementEntryService(
      SecurityEventQueryRepository securityEventQueryRepository,
      TenantQueryRepository tenantQueryRepository,
      AuditLogPublisher auditLogPublisher) {
    Map<String, SecurityEventManagementService<?>> services =
        Map.of(
            "findList", new SecurityEventFindListService(securityEventQueryRepository),
            "get", new SecurityEventFindService(securityEventQueryRepository));

    this.handler = new SecurityEventManagementHandler(services, this, tenantQueryRepository);
    this.auditLogPublisher = auditLogPublisher;
  }

  @Override
  @Transaction(readOnly = true)
  public SecurityEventManagementResponse findList(
      AdminAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      SecurityEventQueries queries,
      RequestAttributes requestAttributes) {

    SecurityEventManagementResult result =
        handler.handle(
            "findList",
            authenticationContext,
            tenantIdentifier,
            new SecurityEventManagementFindListRequest(queries),
            requestAttributes);

    AuditLog auditLog = AuditLogCreator.create(result.context());
    auditLogPublisher.publish(auditLog);

    return result.toResponse();
  }

  @Override
  @Transaction(readOnly = true)
  public SecurityEventManagementResponse get(
      AdminAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      SecurityEventIdentifier identifier,
      RequestAttributes requestAttributes) {

    SecurityEventManagementResult result =
        handler.handle(
            "get",
            authenticationContext,
            tenantIdentifier,
            new SecurityEventManagementFindRequest(identifier),
            requestAttributes);

    AuditLog auditLog = AuditLogCreator.create(result.context());
    auditLogPublisher.publish(auditLog);

    return result.toResponse();
  }
}
