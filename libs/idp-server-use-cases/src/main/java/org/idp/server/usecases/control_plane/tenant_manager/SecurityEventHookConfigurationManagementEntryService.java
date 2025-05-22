/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.usecases.control_plane.tenant_manager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.control_plane.base.definition.AdminPermissions;
import org.idp.server.control_plane.management.security.hook.*;
import org.idp.server.control_plane.management.security.hook.io.SecurityEventHookConfigManagementResponse;
import org.idp.server.control_plane.management.security.hook.io.SecurityEventHookConfigManagementStatus;
import org.idp.server.control_plane.management.security.hook.io.SecurityEventHookConfigRequest;
import org.idp.server.core.oidc.identity.User;
import org.idp.server.core.oidc.token.OAuthToken;
import org.idp.server.platform.datasource.Transaction;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantQueryRepository;
import org.idp.server.platform.security.hook.SecurityEventHookConfiguration;
import org.idp.server.platform.security.hook.SecurityEventHookConfigurationIdentifier;
import org.idp.server.platform.security.repository.SecurityEventHookConfigurationCommandRepository;
import org.idp.server.platform.security.repository.SecurityEventHookConfigurationQueryRepository;
import org.idp.server.platform.security.type.RequestAttributes;

@Transaction
public class SecurityEventHookConfigurationManagementEntryService
    implements SecurityEventHookConfigurationManagementApi {

  SecurityEventHookConfigurationCommandRepository securityEventHookConfigurationCommandRepository;
  SecurityEventHookConfigurationQueryRepository securityEventHookConfigurationQueryRepository;
  TenantQueryRepository tenantQueryRepository;
  LoggerWrapper log =
      LoggerWrapper.getLogger(SecurityEventHookConfigurationManagementEntryService.class);

  public SecurityEventHookConfigurationManagementEntryService(
      SecurityEventHookConfigurationCommandRepository
          securityEventHookConfigurationCommandRepository,
      SecurityEventHookConfigurationQueryRepository securityEventHookConfigurationQueryRepository,
      TenantQueryRepository tenantQueryRepository) {
    this.securityEventHookConfigurationCommandRepository =
        securityEventHookConfigurationCommandRepository;
    this.securityEventHookConfigurationQueryRepository =
        securityEventHookConfigurationQueryRepository;
    this.tenantQueryRepository = tenantQueryRepository;
  }

  @Override
  public SecurityEventHookConfigManagementResponse create(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      SecurityEventHookConfigRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {
    AdminPermissions permissions = getRequiredPermissions("create");
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

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

    SecurityEventHookConfigRegistrationContextCreator contextCreator =
        new SecurityEventHookConfigRegistrationContextCreator(tenant, request, dryRun);
    SecurityEventHookConfigRegistrationContext context = contextCreator.create();

    if (context.isDryRun()) {
      return context.toResponse();
    }

    securityEventHookConfigurationCommandRepository.register(tenant, context.configuration());

    return context.toResponse();
  }

  @Transaction(readOnly = true)
  @Override
  public SecurityEventHookConfigManagementResponse findList(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      int limit,
      int offset,
      RequestAttributes requestAttributes) {
    AdminPermissions permissions = getRequiredPermissions("findList");
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

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

    List<SecurityEventHookConfiguration> configurations =
        securityEventHookConfigurationQueryRepository.findList(tenant, limit, offset);
    Map<String, Object> response = new HashMap<>();
    response.put(
        "list", configurations.stream().map(SecurityEventHookConfiguration::payload).toList());

    return new SecurityEventHookConfigManagementResponse(
        SecurityEventHookConfigManagementStatus.OK, response);
  }

  @Transaction(readOnly = true)
  @Override
  public SecurityEventHookConfigManagementResponse get(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      SecurityEventHookConfigurationIdentifier identifier,
      RequestAttributes requestAttributes) {
    AdminPermissions permissions = getRequiredPermissions("get");
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

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

    SecurityEventHookConfiguration configuration =
        securityEventHookConfigurationQueryRepository.find(tenant, identifier);

    if (!configuration.exists()) {
      return new SecurityEventHookConfigManagementResponse(
          SecurityEventHookConfigManagementStatus.NOT_FOUND, Map.of());
    }

    return new SecurityEventHookConfigManagementResponse(
        SecurityEventHookConfigManagementStatus.OK, configuration.payload());
  }

  @Override
  public SecurityEventHookConfigManagementResponse update(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      SecurityEventHookConfigurationIdentifier identifier,
      SecurityEventHookConfigRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {
    AdminPermissions permissions = getRequiredPermissions("update");
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

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);
    SecurityEventHookConfiguration before =
        securityEventHookConfigurationQueryRepository.find(tenant, identifier);

    if (!before.exists()) {
      return new SecurityEventHookConfigManagementResponse(
          SecurityEventHookConfigManagementStatus.NOT_FOUND, Map.of());
    }

    SecurityEventHookConfigUpdateContextCreator contextCreator =
        new SecurityEventHookConfigUpdateContextCreator(tenant, before, request, dryRun);
    SecurityEventHookConfigUpdateContext context = contextCreator.create();

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

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

    SecurityEventHookConfiguration configuration =
        securityEventHookConfigurationQueryRepository.find(tenant, identifier);

    if (configuration.exists()) {
      return new SecurityEventHookConfigManagementResponse(
          SecurityEventHookConfigManagementStatus.NOT_FOUND, Map.of());
    }

    securityEventHookConfigurationCommandRepository.delete(tenant, configuration);

    return new SecurityEventHookConfigManagementResponse(
        SecurityEventHookConfigManagementStatus.OK, configuration.payload());
  }
}
