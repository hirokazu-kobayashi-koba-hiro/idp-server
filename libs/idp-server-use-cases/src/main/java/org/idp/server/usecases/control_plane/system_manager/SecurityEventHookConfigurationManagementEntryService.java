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
import org.idp.server.control_plane.management.security.hook.*;
import org.idp.server.control_plane.management.security.hook.handler.*;
import org.idp.server.control_plane.management.security.hook.io.SecurityEventHookConfigManagementResponse;
import org.idp.server.control_plane.management.security.hook.io.SecurityEventHookRequest;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.audit.AuditLog;
import org.idp.server.platform.audit.AuditLogPublisher;
import org.idp.server.platform.datasource.Transaction;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantQueryRepository;
import org.idp.server.platform.security.hook.configuration.SecurityEventHookConfigurationIdentifier;
import org.idp.server.platform.security.repository.SecurityEventHookConfigurationCommandRepository;
import org.idp.server.platform.security.repository.SecurityEventHookConfigurationQueryRepository;
import org.idp.server.platform.type.RequestAttributes;

@Transaction
public class SecurityEventHookConfigurationManagementEntryService
    implements SecurityEventHookConfigurationManagementApi {

  private final SecurityEventHookConfigManagementHandler handler;
  private final AuditLogPublisher auditLogPublisher;

  public SecurityEventHookConfigurationManagementEntryService(
      SecurityEventHookConfigurationCommandRepository
          securityEventHookConfigurationCommandRepository,
      SecurityEventHookConfigurationQueryRepository securityEventHookConfigurationQueryRepository,
      TenantQueryRepository tenantQueryRepository,
      AuditLogPublisher auditLogPublisher) {

    Map<String, SecurityEventHookConfigManagementService<?>> services = new HashMap<>();
    services.put(
        "create",
        new SecurityEventHookConfigCreateService(securityEventHookConfigurationCommandRepository));
    services.put(
        "findList",
        new SecurityEventHookConfigFindListService(securityEventHookConfigurationQueryRepository));
    services.put(
        "get",
        new SecurityEventHookConfigFindService(securityEventHookConfigurationQueryRepository));
    services.put(
        "update",
        new SecurityEventHookConfigUpdateService(
            securityEventHookConfigurationQueryRepository,
            securityEventHookConfigurationCommandRepository));
    services.put(
        "delete",
        new SecurityEventHookConfigDeleteService(
            securityEventHookConfigurationQueryRepository,
            securityEventHookConfigurationCommandRepository));

    this.handler =
        new SecurityEventHookConfigManagementHandler(services, this, tenantQueryRepository);
    this.auditLogPublisher = auditLogPublisher;
  }

  @Override
  public SecurityEventHookConfigManagementResponse create(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      SecurityEventHookRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    SecurityEventHookConfigManagementResult result =
        handler.handle(
            "create", tenantIdentifier, operator, oAuthToken, request, requestAttributes, dryRun);

    if (result.hasException()) {
      AuditLog auditLog =
          AuditLogCreator.createOnError(
              "SecurityEventHookConfigurationManagementApi.create",
              result.tenant(),
              operator,
              oAuthToken,
              result.getException(),
              requestAttributes);
      auditLogPublisher.publish(auditLog);
      return result.toResponse(dryRun);
    }

    AuditLog auditLog = AuditLogCreator.create(result.context());
    auditLogPublisher.publish(auditLog);

    return result.toResponse(dryRun);
  }

  @Override
  @Transaction(readOnly = true)
  public SecurityEventHookConfigManagementResponse findList(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      int limit,
      int offset,
      RequestAttributes requestAttributes) {

    SecurityEventHookConfigFindListRequest request =
        new SecurityEventHookConfigFindListRequest(limit, offset);
    SecurityEventHookConfigManagementResult result =
        handler.handle(
            "findList", tenantIdentifier, operator, oAuthToken, request, requestAttributes, false);

    if (result.hasException()) {
      AuditLog auditLog =
          AuditLogCreator.createOnError(
              "SecurityEventHookConfigurationManagementApi.findList",
              result.tenant(),
              operator,
              oAuthToken,
              result.getException(),
              requestAttributes);
      auditLogPublisher.publish(auditLog);
      return result.toResponse(false);
    }

    AuditLog auditLog =
        AuditLogCreator.createOnRead(
            "SecurityEventHookConfigurationManagementApi.findList",
            "findList",
            result.tenant(),
            operator,
            oAuthToken,
            requestAttributes);
    auditLogPublisher.publish(auditLog);

    return result.toResponse(false);
  }

  @Override
  @Transaction(readOnly = true)
  public SecurityEventHookConfigManagementResponse get(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      SecurityEventHookConfigurationIdentifier identifier,
      RequestAttributes requestAttributes) {

    SecurityEventHookConfigManagementResult result =
        handler.handle(
            "get", tenantIdentifier, operator, oAuthToken, identifier, requestAttributes, false);

    if (result.hasException()) {
      AuditLog auditLog =
          AuditLogCreator.createOnError(
              "SecurityEventHookConfigurationManagementApi.get",
              result.tenant(),
              operator,
              oAuthToken,
              result.getException(),
              requestAttributes);
      auditLogPublisher.publish(auditLog);
      return result.toResponse(false);
    }

    AuditLog auditLog =
        AuditLogCreator.createOnRead(
            "SecurityEventHookConfigurationManagementApi.get",
            "get",
            result.tenant(),
            operator,
            oAuthToken,
            requestAttributes);
    auditLogPublisher.publish(auditLog);

    return result.toResponse(false);
  }

  @Override
  public SecurityEventHookConfigManagementResponse update(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      SecurityEventHookConfigurationIdentifier identifier,
      SecurityEventHookRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    SecurityEventHookConfigUpdateRequest updateRequest =
        new SecurityEventHookConfigUpdateRequest(identifier, request);
    SecurityEventHookConfigManagementResult result =
        handler.handle(
            "update",
            tenantIdentifier,
            operator,
            oAuthToken,
            updateRequest,
            requestAttributes,
            dryRun);

    if (result.hasException()) {
      AuditLog auditLog =
          AuditLogCreator.createOnError(
              "SecurityEventHookConfigurationManagementApi.update",
              result.tenant(),
              operator,
              oAuthToken,
              result.getException(),
              requestAttributes);
      auditLogPublisher.publish(auditLog);
      return result.toResponse(dryRun);
    }

    AuditLog auditLog = AuditLogCreator.create(result.context());
    auditLogPublisher.publish(auditLog);

    return result.toResponse(dryRun);
  }

  @Override
  public SecurityEventHookConfigManagementResponse delete(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      SecurityEventHookConfigurationIdentifier identifier,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    SecurityEventHookConfigManagementResult result =
        handler.handle(
            "delete",
            tenantIdentifier,
            operator,
            oAuthToken,
            identifier,
            requestAttributes,
            dryRun);

    if (result.hasException()) {
      AuditLog auditLog =
          AuditLogCreator.createOnError(
              "SecurityEventHookConfigurationManagementApi.delete",
              result.tenant(),
              operator,
              oAuthToken,
              result.getException(),
              requestAttributes);
      auditLogPublisher.publish(auditLog);
      return result.toResponse(dryRun);
    }

    AuditLog auditLog =
        AuditLogCreator.createOnDeletion(
            "SecurityEventHookConfigurationManagementApi.delete",
            "delete",
            result.tenant(),
            operator,
            oAuthToken,
            (Map<String, Object>) result.context(),
            requestAttributes);
    auditLogPublisher.publish(auditLog);

    return result.toResponse(dryRun);
  }
}
