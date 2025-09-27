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
import org.idp.server.control_plane.management.security.hook.*;
import org.idp.server.control_plane.management.security.hook.io.SecurityEventHookConfigManagementResponse;
import org.idp.server.control_plane.management.security.hook.io.SecurityEventHookConfigManagementStatus;
import org.idp.server.control_plane.management.security.hook.io.SecurityEventHookRequest;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.audit.AuditLog;
import org.idp.server.platform.audit.AuditLogPublisher;
import org.idp.server.platform.datasource.Transaction;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantQueryRepository;
import org.idp.server.platform.security.hook.configuration.SecurityEventHookConfiguration;
import org.idp.server.platform.security.hook.configuration.SecurityEventHookConfigurationIdentifier;
import org.idp.server.platform.security.repository.SecurityEventHookConfigurationCommandRepository;
import org.idp.server.platform.security.repository.SecurityEventHookConfigurationQueryRepository;
import org.idp.server.platform.type.RequestAttributes;

@Transaction
public class SecurityEventHookConfigurationManagementEntryService
    implements SecurityEventHookConfigurationManagementApi {

  SecurityEventHookConfigurationCommandRepository securityEventHookConfigurationCommandRepository;
  SecurityEventHookConfigurationQueryRepository securityEventHookConfigurationQueryRepository;
  TenantQueryRepository tenantQueryRepository;
  AuditLogPublisher auditLogPublisher;
  LoggerWrapper log =
      LoggerWrapper.getLogger(SecurityEventHookConfigurationManagementEntryService.class);

  public SecurityEventHookConfigurationManagementEntryService(
      SecurityEventHookConfigurationCommandRepository
          securityEventHookConfigurationCommandRepository,
      SecurityEventHookConfigurationQueryRepository securityEventHookConfigurationQueryRepository,
      TenantQueryRepository tenantQueryRepository,
      AuditLogPublisher auditLogPublisher) {
    this.securityEventHookConfigurationCommandRepository =
        securityEventHookConfigurationCommandRepository;
    this.securityEventHookConfigurationQueryRepository =
        securityEventHookConfigurationQueryRepository;
    this.tenantQueryRepository = tenantQueryRepository;
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
    AdminPermissions permissions = getRequiredPermissions("create");

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

    SecurityEventHookConfigRegistrationContextCreator contextCreator =
        new SecurityEventHookConfigRegistrationContextCreator(tenant, request, dryRun);
    SecurityEventHookConfigRegistrationContext context = contextCreator.create();

    AuditLog auditLog =
        AuditLogCreator.create(
            "SecurityEventHookConfigurationManagementApi.create",
            tenant,
            operator,
            oAuthToken,
            context,
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
      return new SecurityEventHookConfigManagementResponse(
          SecurityEventHookConfigManagementStatus.FORBIDDEN, response);
    }

    if (context.isDryRun()) {
      return context.toResponse();
    }

    securityEventHookConfigurationCommandRepository.register(tenant, context.configuration());

    return context.toResponse();
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
    AdminPermissions permissions = getRequiredPermissions("findList");

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);
    List<SecurityEventHookConfiguration> configurations =
        securityEventHookConfigurationQueryRepository.findList(tenant, limit, offset);

    AuditLog auditLog =
        AuditLogCreator.createOnRead(
            "SecurityEventHookConfigurationManagementApi.findList",
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
      return new SecurityEventHookConfigManagementResponse(
          SecurityEventHookConfigManagementStatus.FORBIDDEN, response);
    }

    Map<String, Object> response = new HashMap<>();
    response.put(
        "list", configurations.stream().map(SecurityEventHookConfiguration::toMap).toList());

    return new SecurityEventHookConfigManagementResponse(
        SecurityEventHookConfigManagementStatus.OK, response);
  }

  @Override
  @Transaction(readOnly = true)
  public SecurityEventHookConfigManagementResponse get(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      SecurityEventHookConfigurationIdentifier identifier,
      RequestAttributes requestAttributes) {
    AdminPermissions permissions = getRequiredPermissions("get");

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);
    SecurityEventHookConfiguration configuration =
        securityEventHookConfigurationQueryRepository.findWithDisabled(tenant, identifier, true);

    AuditLog auditLog =
        AuditLogCreator.createOnRead(
            "SecurityEventHookConfigurationManagementApi.get",
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
      return new SecurityEventHookConfigManagementResponse(
          SecurityEventHookConfigManagementStatus.FORBIDDEN, response);
    }

    if (!configuration.exists()) {
      return new SecurityEventHookConfigManagementResponse(
          SecurityEventHookConfigManagementStatus.NOT_FOUND, Map.of());
    }

    return new SecurityEventHookConfigManagementResponse(
        SecurityEventHookConfigManagementStatus.OK, configuration.toMap());
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
    AdminPermissions permissions = getRequiredPermissions("update");

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);
    SecurityEventHookConfiguration before =
        securityEventHookConfigurationQueryRepository.findWithDisabled(tenant, identifier, true);

    SecurityEventHookConfigUpdateContextCreator contextCreator =
        new SecurityEventHookConfigUpdateContextCreator(
            tenant, before, identifier, request, dryRun);
    SecurityEventHookConfigUpdateContext context = contextCreator.create();

    AuditLog auditLog =
        AuditLogCreator.createOnUpdate(
            "SecurityEventHookConfigurationManagementApi.update",
            tenant,
            operator,
            oAuthToken,
            context,
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
      return new SecurityEventHookConfigManagementResponse(
          SecurityEventHookConfigManagementStatus.FORBIDDEN, response);
    }

    if (!before.exists()) {
      return new SecurityEventHookConfigManagementResponse(
          SecurityEventHookConfigManagementStatus.NOT_FOUND, Map.of());
    }

    if (context.isDryRun()) {
      return context.toResponse();
    }

    securityEventHookConfigurationCommandRepository.update(tenant, context.after());

    return context.toResponse();
  }

  @Override
  public SecurityEventHookConfigManagementResponse delete(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      SecurityEventHookConfigurationIdentifier identifier,
      RequestAttributes requestAttributes,
      boolean dryRun) {
    AdminPermissions permissions = getRequiredPermissions("delete");

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);
    SecurityEventHookConfiguration configuration =
        securityEventHookConfigurationQueryRepository.findWithDisabled(tenant, identifier, true);

    AuditLog auditLog =
        AuditLogCreator.createOnDeletion(
            "SecurityEventHookConfigurationManagementApi.delete",
            "delete",
            tenant,
            operator,
            oAuthToken,
            configuration.toMap(),
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
      return new SecurityEventHookConfigManagementResponse(
          SecurityEventHookConfigManagementStatus.FORBIDDEN, response);
    }

    if (!configuration.exists()) {
      return new SecurityEventHookConfigManagementResponse(
          SecurityEventHookConfigManagementStatus.NOT_FOUND, Map.of());
    }

    securityEventHookConfigurationCommandRepository.delete(tenant, configuration);

    return new SecurityEventHookConfigManagementResponse(
        SecurityEventHookConfigManagementStatus.NO_CONTENT, null);
  }
}
