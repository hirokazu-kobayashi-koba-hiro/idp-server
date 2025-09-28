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
import java.util.List;
import java.util.Map;
import org.idp.server.control_plane.base.AuditLogCreator;
import org.idp.server.control_plane.base.definition.AdminPermissions;
import org.idp.server.control_plane.management.security.hook_result.SecurityEventHookManagementApi;
import org.idp.server.control_plane.management.security.hook_result.io.SecurityEventHookManagementResponse;
import org.idp.server.control_plane.management.security.hook_result.io.SecurityEventHookManagementStatus;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.audit.AuditLog;
import org.idp.server.platform.audit.AuditLogPublisher;
import org.idp.server.platform.datasource.Transaction;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantQueryRepository;
import org.idp.server.platform.security.SecurityEvent;
import org.idp.server.platform.security.hook.*;
import org.idp.server.platform.security.hook.configuration.SecurityEventHookConfiguration;
import org.idp.server.platform.security.repository.SecurityEventHookConfigurationQueryRepository;
import org.idp.server.platform.security.repository.SecurityEventHookResultCommandRepository;
import org.idp.server.platform.security.repository.SecurityEventHookResultQueryRepository;
import org.idp.server.platform.type.RequestAttributes;

@Transaction
public class SecurityEventHookManagementEntryService implements SecurityEventHookManagementApi {

  TenantQueryRepository tenantQueryRepository;
  SecurityEventHookResultQueryRepository securityEventHookResultRepository;
  SecurityEventHookResultCommandRepository securityEventHookResultCommandRepository;
  SecurityEventHooks securityEventHooks;
  SecurityEventHookConfigurationQueryRepository securityEventHookConfigurationQueryRepository;
  AuditLogPublisher auditLogPublisher;
  LoggerWrapper log = LoggerWrapper.getLogger(SecurityEventHookManagementEntryService.class);

  public SecurityEventHookManagementEntryService(
      TenantQueryRepository tenantQueryRepository,
      SecurityEventHookResultQueryRepository securityEventHookResultQueryRepository,
      SecurityEventHookResultCommandRepository securityEventHookResultCommandRepository,
      SecurityEventHooks securityEventHooks,
      SecurityEventHookConfigurationQueryRepository securityEventHookConfigurationQueryRepository,
      AuditLogPublisher auditLogPublisher) {
    this.tenantQueryRepository = tenantQueryRepository;
    this.securityEventHookResultRepository = securityEventHookResultQueryRepository;
    this.securityEventHookResultCommandRepository = securityEventHookResultCommandRepository;
    this.securityEventHooks = securityEventHooks;
    this.securityEventHookConfigurationQueryRepository =
        securityEventHookConfigurationQueryRepository;
    this.auditLogPublisher = auditLogPublisher;
  }

  @Override
  @Transaction(readOnly = true)
  public SecurityEventHookManagementResponse findList(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      SecurityEventHookResultQueries queries,
      RequestAttributes requestAttributes) {
    AdminPermissions permissions = getRequiredPermissions("findList");
    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);
    AuditLog auditLog =
        AuditLogCreator.createOnRead(
            "SecurityEventHookManagementApi.findList",
            "findList",
            tenant,
            operator,
            oAuthToken,
            requestAttributes);
    auditLogPublisher.publish(auditLog);

    if (!permissions.includesAll(operator.permissionsAsSet())) {
      Map<String, Object> response = new HashMap<>();
      response.put("error", "access_denied");
      response.put(
          "error_description",
          String.format(
              "permission denied required permission %s, but %s",
              permissions.valuesAsString(), operator.permissionsAsString()));
      log.warn(response.toString());
      return new SecurityEventHookManagementResponse(
          SecurityEventHookManagementStatus.FORBIDDEN, response);
    }

    long totalCount = securityEventHookResultRepository.findTotalCount(tenant, queries);
    if (totalCount == 0) {
      Map<String, Object> response = new HashMap<>();
      response.put("list", List.of());
      response.put("total_count", 0);
      response.put("limit", queries.limit());
      response.put("offset", queries.offset());
      return new SecurityEventHookManagementResponse(
          SecurityEventHookManagementStatus.OK, response);
    }

    List<SecurityEventHookResult> events =
        securityEventHookResultRepository.findList(tenant, queries);

    Map<String, Object> response = new HashMap<>();
    response.put("list", events.stream().map(SecurityEventHookResult::toMap).toList());
    response.put("total_count", totalCount);
    response.put("limit", queries.limit());
    response.put("offset", queries.offset());

    return new SecurityEventHookManagementResponse(SecurityEventHookManagementStatus.OK, response);
  }

  @Override
  @Transaction(readOnly = true)
  public SecurityEventHookManagementResponse get(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      SecurityEventHookResultIdentifier identifier,
      RequestAttributes requestAttributes) {
    AdminPermissions permissions = getRequiredPermissions("get");

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);
    SecurityEventHookResult hookResult = securityEventHookResultRepository.find(tenant, identifier);

    AuditLog auditLog =
        AuditLogCreator.createOnRead(
            "SecurityEventHookManagementApi.get",
            "get",
            tenant,
            operator,
            oAuthToken,
            requestAttributes);
    auditLogPublisher.publish(auditLog);

    if (!permissions.includesAll(operator.permissionsAsSet())) {
      Map<String, Object> response = new HashMap<>();
      response.put("error", "access_denied");
      response.put(
          "error_description",
          String.format(
              "permission denied required permission %s, but %s",
              permissions.valuesAsString(), operator.permissionsAsString()));
      log.warn(response.toString());
      return new SecurityEventHookManagementResponse(
          SecurityEventHookManagementStatus.FORBIDDEN, response);
    }

    if (!hookResult.exists()) {
      return new SecurityEventHookManagementResponse(
          SecurityEventHookManagementStatus.NOT_FOUND, Map.of());
    }

    return new SecurityEventHookManagementResponse(
        SecurityEventHookManagementStatus.OK, hookResult.toMap());
  }

  /**
   * Entry service for security event hook retry management operations.
   *
   * <p>This service handles manual retry operations for failed security event hook executions,
   * providing system administrators with the ability to re-execute failed hooks after resolving
   * underlying issues.
   */
  @Override
  public SecurityEventHookManagementResponse retry(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      SecurityEventHookResultIdentifier identifier,
      RequestAttributes requestAttributes) {

    AdminPermissions permissions = getRequiredPermissions("retry");

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);
    SecurityEventHookResult previousHookResult =
        securityEventHookResultRepository.find(tenant, identifier);

    if (!permissions.includesAll(operator.permissionsAsSet())) {
      Map<String, Object> response = new HashMap<>();
      response.put("error", "access_denied");
      response.put(
          "error_description",
          String.format(
              "permission denied required permission %s, but %s",
              permissions.valuesAsString(), operator.permissionsAsString()));
      log.warn(response.toString());
      return new SecurityEventHookManagementResponse(
          SecurityEventHookManagementStatus.FORBIDDEN, response);
    }

    if (!previousHookResult.exists()) {
      return new SecurityEventHookManagementResponse(
          SecurityEventHookManagementStatus.NOT_FOUND, Map.of());
    }

    SecurityEventHookType hookType = previousHookResult.type();
    SecurityEvent securityEvent = previousHookResult.securityEvent();
    SecurityEventHook securityEventHook = securityEventHooks.get(hookType);

    SecurityEventHookConfiguration securityEventHookConfiguration =
        securityEventHookConfigurationQueryRepository.find(tenant, hookType.name());
    SecurityEventHookResult securityEventHookResult =
        securityEventHook.execute(tenant, securityEvent, securityEventHookConfiguration);

    SecurityEventHookStatus retryStatus =
        securityEventHookResult.isSuccess()
            ? SecurityEventHookStatus.RETRY_SUCCESS
            : SecurityEventHookStatus.RETRY_FAILURE;
    securityEventHookResultCommandRepository.updateStatus(tenant, previousHookResult, retryStatus);
    securityEventHookResultCommandRepository.register(tenant, securityEventHookResult);

    AuditLog auditLog =
        AuditLogCreator.createOnRead(
            "SecurityEventHookManagementApi.retry",
            "retry",
            tenant,
            operator,
            oAuthToken,
            requestAttributes);
    auditLogPublisher.publish(auditLog);

    return new SecurityEventHookManagementResponse(
        SecurityEventHookManagementStatus.OK, securityEventHookResult.toMap());
  }
}
