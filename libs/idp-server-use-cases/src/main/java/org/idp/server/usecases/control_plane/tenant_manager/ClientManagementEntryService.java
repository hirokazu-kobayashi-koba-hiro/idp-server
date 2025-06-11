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

package org.idp.server.usecases.control_plane.tenant_manager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.control_plane.base.AuditLogCreator;
import org.idp.server.control_plane.base.definition.AdminPermissions;
import org.idp.server.control_plane.management.oidc.client.*;
import org.idp.server.control_plane.management.oidc.client.io.ClientManagementResponse;
import org.idp.server.control_plane.management.oidc.client.io.ClientManagementStatus;
import org.idp.server.control_plane.management.oidc.client.io.ClientRegistrationRequest;
import org.idp.server.control_plane.management.oidc.client.validator.ClientRegistrationRequestValidationResult;
import org.idp.server.control_plane.management.oidc.client.validator.ClientRegistrationRequestValidator;
import org.idp.server.core.oidc.client.ClientIdentifier;
import org.idp.server.core.oidc.configuration.client.ClientConfiguration;
import org.idp.server.core.oidc.configuration.client.ClientConfigurationCommandRepository;
import org.idp.server.core.oidc.configuration.client.ClientConfigurationQueryRepository;
import org.idp.server.core.oidc.identity.User;
import org.idp.server.core.oidc.token.OAuthToken;
import org.idp.server.platform.audit.AuditLog;
import org.idp.server.platform.audit.AuditLogWriters;
import org.idp.server.platform.datasource.Transaction;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantQueryRepository;
import org.idp.server.platform.security.type.RequestAttributes;

@Transaction
public class ClientManagementEntryService implements ClientManagementApi {

  TenantQueryRepository tenantQueryRepository;
  ClientConfigurationCommandRepository clientConfigurationCommandRepository;
  ClientConfigurationQueryRepository clientConfigurationQueryRepository;
  AuditLogWriters auditLogWriters;
  LoggerWrapper log = LoggerWrapper.getLogger(ClientManagementEntryService.class);

  public ClientManagementEntryService(
      TenantQueryRepository tenantQueryRepository,
      ClientConfigurationCommandRepository clientConfigurationCommandRepository,
      ClientConfigurationQueryRepository clientConfigurationQueryRepository,
      AuditLogWriters auditLogWriters) {
    this.tenantQueryRepository = tenantQueryRepository;
    this.clientConfigurationCommandRepository = clientConfigurationCommandRepository;
    this.clientConfigurationQueryRepository = clientConfigurationQueryRepository;
    this.auditLogWriters = auditLogWriters;
  }

  public ClientManagementResponse create(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      ClientRegistrationRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    AdminPermissions permissions = getRequiredPermissions("create");

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);
    ClientRegistrationRequestValidator validator =
        new ClientRegistrationRequestValidator(request, dryRun);
    ClientRegistrationRequestValidationResult validate = validator.validate();

    ClientRegistrationContextCreator contextCreator =
        new ClientRegistrationContextCreator(tenant, request, dryRun);
    ClientRegistrationContext context = contextCreator.create();

    AuditLog auditLog =
        AuditLogCreator.create(
            "ClientManagementApi.create", tenant, operator, oAuthToken, context, requestAttributes);
    auditLogWriters.write(tenant, auditLog);

    if (!permissions.includesAll(operator.permissionsAsSet())) {
      Map<String, Object> response = new HashMap<>();
      response.put("error", "access_denied");
      response.put(
          "error_description",
          String.format(
              "permission denied required permission %s, but %s",
              permissions.valuesAsString(), operator.permissionsAsString()));
      log.warn(response.toString());
      return new ClientManagementResponse(ClientManagementStatus.FORBIDDEN, response);
    }

    if (!validate.isValid()) {
      return validate.errorResponse();
    }

    if (dryRun) {
      return context.toResponse();
    }

    clientConfigurationCommandRepository.register(tenant, context.configuration());

    return context.toResponse();
  }

  @Override
  public ClientManagementResponse findList(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      int limit,
      int offset,
      RequestAttributes requestAttributes) {

    AdminPermissions permissions = getRequiredPermissions("findList");

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);
    List<ClientConfiguration> clientConfigurations =
        clientConfigurationQueryRepository.findList(tenant, limit, offset);

    AuditLog auditLog =
        AuditLogCreator.createOnRead(
            "ClientManagementApi.findList",
            "findList",
            tenant,
            operator,
            oAuthToken,
            requestAttributes);
    auditLogWriters.write(tenant, auditLog);

    if (!permissions.includesAll(operator.permissionsAsSet())) {
      Map<String, Object> response = new HashMap<>();
      response.put("error", "access_denied");
      response.put(
          "error_description",
          String.format(
              "permission denied required permission %s, but %s",
              permissions.valuesAsString(), operator.permissionsAsString()));
      log.warn(response.toString());
      return new ClientManagementResponse(ClientManagementStatus.FORBIDDEN, response);
    }

    Map<String, Object> response = new HashMap<>();
    response.put("list", clientConfigurations.stream().map(ClientConfiguration::toMap).toList());

    return new ClientManagementResponse(ClientManagementStatus.OK, response);
  }

  @Override
  public ClientManagementResponse get(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      ClientIdentifier clientIdentifier,
      RequestAttributes requestAttributes) {

    AdminPermissions permissions = getRequiredPermissions("get");

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);
    ClientConfiguration clientConfiguration =
        clientConfigurationQueryRepository.find(tenant, clientIdentifier);

    AuditLog auditLog =
        AuditLogCreator.createOnRead(
            "ClientManagementApi.get", "get", tenant, operator, oAuthToken, requestAttributes);
    auditLogWriters.write(tenant, auditLog);

    if (!permissions.includesAll(operator.permissionsAsSet())) {
      Map<String, Object> response = new HashMap<>();
      response.put("error", "access_denied");
      response.put(
          "error_description",
          String.format(
              "permission denied required permission %s, but %s",
              permissions.valuesAsString(), operator.permissionsAsString()));
      log.warn(response.toString());
      return new ClientManagementResponse(ClientManagementStatus.FORBIDDEN, response);
    }

    if (!clientConfiguration.exists()) {
      return new ClientManagementResponse(ClientManagementStatus.NOT_FOUND, Map.of());
    }

    return new ClientManagementResponse(ClientManagementStatus.OK, clientConfiguration.toMap());
  }

  @Override
  public ClientManagementResponse update(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      ClientIdentifier clientIdentifier,
      ClientRegistrationRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    AdminPermissions permissions = getRequiredPermissions("update");

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);
    ClientConfiguration before = clientConfigurationQueryRepository.find(tenant, clientIdentifier);

    ClientRegistrationRequestValidator validator =
        new ClientRegistrationRequestValidator(request, dryRun);
    ClientRegistrationRequestValidationResult validate = validator.validate();

    ClientUpdateContextCreator contextCreator =
        new ClientUpdateContextCreator(tenant, before, request, dryRun);
    ClientUpdateContext context = contextCreator.create();

    AuditLog auditLog =
        AuditLogCreator.createOnUpdate(
            "ClientManagementApi.update", tenant, operator, oAuthToken, context, requestAttributes);
    auditLogWriters.write(tenant, auditLog);

    if (!permissions.includesAll(operator.permissionsAsSet())) {
      Map<String, Object> response = new HashMap<>();
      response.put("error", "access_denied");
      response.put(
          "error_description",
          String.format(
              "permission denied required permission %s, but %s",
              permissions.valuesAsString(), operator.permissionsAsString()));
      log.warn(response.toString());
      return new ClientManagementResponse(ClientManagementStatus.FORBIDDEN, response);
    }

    if (!before.exists()) {
      return new ClientManagementResponse(ClientManagementStatus.NOT_FOUND, Map.of());
    }

    if (!validate.isValid()) {
      return validate.errorResponse();
    }

    if (dryRun) {
      return context.toResponse();
    }

    clientConfigurationCommandRepository.update(tenant, context.after());

    return context.toResponse();
  }

  @Override
  public ClientManagementResponse delete(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      ClientIdentifier clientIdentifier,
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
      return new ClientManagementResponse(ClientManagementStatus.FORBIDDEN, response);
    }

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);
    ClientConfiguration clientConfiguration =
        clientConfigurationQueryRepository.find(tenant, clientIdentifier);

    AuditLog auditLog =
        AuditLogCreator.createOnDeletion(
            "ClientManagementApi.delete",
            "delete",
            tenant,
            operator,
            oAuthToken,
            clientConfiguration.toMap(),
            requestAttributes);
    auditLogWriters.write(tenant, auditLog);

    if (!clientConfiguration.exists()) {
      return new ClientManagementResponse(ClientManagementStatus.NOT_FOUND, Map.of());
    }

    clientConfigurationCommandRepository.delete(tenant, clientConfiguration);

    return new ClientManagementResponse(ClientManagementStatus.NO_CONTENT, Map.of());
  }
}
