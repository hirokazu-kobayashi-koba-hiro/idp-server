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
import org.idp.server.control_plane.base.AuditLogCreator;
import org.idp.server.control_plane.management.audit.AuditLogManagementApi;
import org.idp.server.control_plane.management.audit.handler.*;
import org.idp.server.control_plane.management.audit.io.AuditLogManagementResponse;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.audit.*;
import org.idp.server.platform.datasource.Transaction;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantQueryRepository;
import org.idp.server.platform.type.RequestAttributes;

@Transaction
public class AuditLogManagementEntryService implements AuditLogManagementApi {

  AuditLogPublisher auditLogPublisher;
  LoggerWrapper log = LoggerWrapper.getLogger(AuditLogManagementEntryService.class);

  // Handler/Service pattern
  private AuditLogManagementHandler handler;

  public AuditLogManagementEntryService(
      AuditLogQueryRepository auditLogQueryRepository,
      TenantQueryRepository tenantQueryRepository,
      AuditLogPublisher auditLogPublisher) {
    this.auditLogPublisher = auditLogPublisher;

    this.handler = createHandler(auditLogQueryRepository, tenantQueryRepository);
  }

  private AuditLogManagementHandler createHandler(
      AuditLogQueryRepository auditLogQueryRepository,
      TenantQueryRepository tenantQueryRepository) {

    Map<String, AuditLogManagementService<?>> services = new HashMap<>();
    services.put("findList", new AuditLogFindListService(auditLogQueryRepository));
    services.put("get", new AuditLogFindService(auditLogQueryRepository));

    return new AuditLogManagementHandler(services, this, tenantQueryRepository);
  }

  @Override
  @Transaction(readOnly = true)
  public AuditLogManagementResponse findList(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      AuditLogQueries queries,
      RequestAttributes requestAttributes) {

    // Delegate to Handler/Service pattern
    AuditLogFindListRequest request = new AuditLogFindListRequest(queries);
    AuditLogManagementResult result =
        handler.handle(
            "findList", tenantIdentifier, operator, oAuthToken, request, requestAttributes);

    if (result.hasException()) {
      AuditLog auditLog =
          AuditLogCreator.createOnError(
              "AuditLogManagementApi.findList",
              result.tenant(),
              operator,
              oAuthToken,
              result.getException(),
              requestAttributes);
      auditLogPublisher.publish(auditLog);
      return result.toResponse();
    }

    // Record audit log (read operation)
    AuditLog auditLog =
        AuditLogCreator.createOnRead(
            "AuditLogManagementApi.findList",
            "findList",
            result.tenant(),
            operator,
            oAuthToken,
            requestAttributes);
    auditLogPublisher.publish(auditLog);

    return result.toResponse();
  }

  @Override
  @Transaction(readOnly = true)
  public AuditLogManagementResponse get(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      AuditLogIdentifier identifier,
      RequestAttributes requestAttributes) {

    // Delegate to Handler/Service pattern
    AuditLogManagementResult result =
        handler.handle(
            "get", tenantIdentifier, operator, oAuthToken, identifier, requestAttributes);

    if (result.hasException()) {
      AuditLog auditLog =
          AuditLogCreator.createOnError(
              "AuditLogManagementApi.get",
              result.tenant(),
              operator,
              oAuthToken,
              result.getException(),
              requestAttributes);
      auditLogPublisher.publish(auditLog);
      return result.toResponse();
    }

    // Record audit log (read operation)
    AuditLog auditLog =
        AuditLogCreator.createOnRead(
            "AuditLogManagementApi.get",
            "get",
            result.tenant(),
            operator,
            oAuthToken,
            requestAttributes);
    auditLogPublisher.publish(auditLog);

    return result.toResponse();
  }
}
