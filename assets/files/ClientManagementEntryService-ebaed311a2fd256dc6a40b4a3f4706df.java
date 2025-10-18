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
import org.idp.server.control_plane.management.oidc.client.*;
import org.idp.server.control_plane.management.oidc.client.io.ClientManagementResponse;
import org.idp.server.control_plane.management.oidc.client.io.ClientManagementStatus;
import org.idp.server.control_plane.management.oidc.client.io.ClientRegistrationRequest;
import org.idp.server.control_plane.management.oidc.client.validator.ClientRegistrationRequestValidationResult;
import org.idp.server.control_plane.management.oidc.client.validator.ClientRegistrationRequestValidator;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfiguration;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfigurationCommandRepository;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfigurationQueryRepository;
import org.idp.server.core.openid.oauth.configuration.client.ClientIdentifier;
import org.idp.server.core.openid.oauth.configuration.client.ClientQueries;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.audit.AuditLog;
import org.idp.server.platform.audit.AuditLogPublisher;
import org.idp.server.platform.datasource.Transaction;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantQueryRepository;
import org.idp.server.platform.type.RequestAttributes;

@Transaction
public class ClientManagementEntryService implements ClientManagementApi {

  TenantQueryRepository tenantQueryRepository;
  ClientConfigurationCommandRepository clientConfigurationCommandRepository;
  ClientConfigurationQueryRepository clientConfigurationQueryRepository;
  AuditLogPublisher auditLogPublisher;
  LoggerWrapper log = LoggerWrapper.getLogger(ClientManagementEntryService.class);

  public ClientManagementEntryService(
      TenantQueryRepository tenantQueryRepository,
      ClientConfigurationCommandRepository clientConfigurationCommandRepository,
      ClientConfigurationQueryRepository clientConfigurationQueryRepository,
      AuditLogPublisher auditLogPublisher) {
    this.tenantQueryRepository = tenantQueryRepository;
    this.clientConfigurationCommandRepository = clientConfigurationCommandRepository;
    this.clientConfigurationQueryRepository = clientConfigurationQueryRepository;
    this.auditLogPublisher = auditLogPublisher;
  }

  @Override
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
  @Transaction(readOnly = true)
  public ClientManagementResponse findList(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      ClientQueries queries,
      RequestAttributes requestAttributes) {

    AdminPermissions permissions = getRequiredPermissions("findList");

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

    AuditLog auditLog =
        AuditLogCreator.createOnRead(
            "ClientManagementApi.findList",
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
      return new ClientManagementResponse(ClientManagementStatus.FORBIDDEN, response);
    }

    long totalCount = clientConfigurationQueryRepository.findTotalCount(tenant, queries);
    if (totalCount == 0) {
      Map<String, Object> response = new HashMap<>();
      response.put("list", List.of());
      response.put("total_count", totalCount);
      response.put("limit", queries.limit());
      response.put("offset", queries.offset());
      return new ClientManagementResponse(ClientManagementStatus.OK, response);
    }

    List<ClientConfiguration> clientConfigurations =
        clientConfigurationQueryRepository.findList(tenant, queries);

    Map<String, Object> response = new HashMap<>();
    response.put("list", clientConfigurations.stream().map(ClientConfiguration::toMap).toList());
    response.put("total_count", totalCount);
    response.put("limit", queries.limit());
    response.put("offset", queries.offset());

    return new ClientManagementResponse(ClientManagementStatus.OK, response);
  }

  @Override
  @Transaction(readOnly = true)
  public ClientManagementResponse get(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      ClientIdentifier clientIdentifier,
      RequestAttributes requestAttributes) {

    AdminPermissions permissions = getRequiredPermissions("get");

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);
    ClientConfiguration clientConfiguration =
        clientConfigurationQueryRepository.findWithDisabled(tenant, clientIdentifier, true);

    AuditLog auditLog =
        AuditLogCreator.createOnRead(
            "ClientManagementApi.get", "get", tenant, operator, oAuthToken, requestAttributes);
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
    ClientConfiguration before =
        clientConfigurationQueryRepository.findWithDisabled(tenant, clientIdentifier, true);

    ClientRegistrationRequestValidator validator =
        new ClientRegistrationRequestValidator(request, dryRun);
    ClientRegistrationRequestValidationResult validate = validator.validate();

    ClientUpdateContextCreator contextCreator =
        new ClientUpdateContextCreator(tenant, before, request, dryRun);
    ClientUpdateContext context = contextCreator.create();

    AuditLog auditLog =
        AuditLogCreator.createOnUpdate(
            "ClientManagementApi.update", tenant, operator, oAuthToken, context, requestAttributes);
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
        clientConfigurationQueryRepository.findWithDisabled(tenant, clientIdentifier, true);

    AuditLog auditLog =
        AuditLogCreator.createOnDeletion(
            "ClientManagementApi.delete",
            "delete",
            tenant,
            operator,
            oAuthToken,
            clientConfiguration.toMap(),
            requestAttributes);
    auditLogPublisher.publish(auditLog);

    if (!clientConfiguration.exists()) {
      return new ClientManagementResponse(ClientManagementStatus.NOT_FOUND, Map.of());
    }

    if (dryRun) {
      Map<String, Object> response = new HashMap<>();
      response.put("message", "Deletion simulated successfully");
      response.put("client_id", clientConfiguration.clientIdValue());
      response.put("dry_run", true);
      return new ClientManagementResponse(ClientManagementStatus.OK, response);
    }

    clientConfigurationCommandRepository.delete(tenant, clientConfiguration);

    return new ClientManagementResponse(ClientManagementStatus.NO_CONTENT, Map.of());
  }
}
