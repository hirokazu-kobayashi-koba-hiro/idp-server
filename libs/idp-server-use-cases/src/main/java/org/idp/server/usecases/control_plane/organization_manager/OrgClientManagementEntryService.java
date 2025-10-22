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

package org.idp.server.usecases.control_plane.organization_manager;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.control_plane.base.AuditLogCreator;
import org.idp.server.control_plane.management.oidc.client.ClientRegistrationContext;
import org.idp.server.control_plane.management.oidc.client.ClientUpdateContext;
import org.idp.server.control_plane.management.oidc.client.OrgClientManagementApi;
import org.idp.server.control_plane.management.oidc.client.handler.ClientCreationService;
import org.idp.server.control_plane.management.oidc.client.handler.ClientDeletionService;
import org.idp.server.control_plane.management.oidc.client.handler.ClientFindListService;
import org.idp.server.control_plane.management.oidc.client.handler.ClientFindService;
import org.idp.server.control_plane.management.oidc.client.handler.ClientManagementResult;
import org.idp.server.control_plane.management.oidc.client.handler.ClientManagementService;
import org.idp.server.control_plane.management.oidc.client.handler.ClientUpdateRequest;
import org.idp.server.control_plane.management.oidc.client.handler.ClientUpdateService;
import org.idp.server.control_plane.management.oidc.client.handler.OrgClientManagementHandler;
import org.idp.server.control_plane.management.oidc.client.io.ClientManagementResponse;
import org.idp.server.control_plane.management.oidc.client.io.ClientRegistrationRequest;
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
import org.idp.server.platform.multi_tenancy.organization.OrganizationIdentifier;
import org.idp.server.platform.multi_tenancy.organization.OrganizationRepository;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantQueryRepository;
import org.idp.server.platform.type.RequestAttributes;

@Transaction
public class OrgClientManagementEntryService implements OrgClientManagementApi {

  private final OrgClientManagementHandler handler;
  private final TenantQueryRepository tenantQueryRepository;
  private final ClientConfigurationQueryRepository clientConfigurationQueryRepository;
  private final AuditLogPublisher auditLogPublisher;

  public OrgClientManagementEntryService(
      TenantQueryRepository tenantQueryRepository,
      OrganizationRepository organizationRepository,
      ClientConfigurationCommandRepository clientConfigurationCommandRepository,
      ClientConfigurationQueryRepository clientConfigurationQueryRepository,
      AuditLogPublisher auditLogPublisher) {

    Map<String, ClientManagementService<?>> services = new HashMap<>();
    services.put("create", new ClientCreationService(clientConfigurationCommandRepository));
    services.put("findList", new ClientFindListService(clientConfigurationQueryRepository));
    services.put("get", new ClientFindService(clientConfigurationQueryRepository));
    services.put(
        "update",
        new ClientUpdateService(
            clientConfigurationQueryRepository, clientConfigurationCommandRepository));
    services.put(
        "delete",
        new ClientDeletionService(
            clientConfigurationQueryRepository, clientConfigurationCommandRepository));

    this.handler = new OrgClientManagementHandler(services, organizationRepository, this);

    this.tenantQueryRepository = tenantQueryRepository;
    this.clientConfigurationQueryRepository = clientConfigurationQueryRepository;
    this.auditLogPublisher = auditLogPublisher;
  }

  @Override
  public ClientManagementResponse create(
      OrganizationIdentifier organizationIdentifier,
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      ClientRegistrationRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

    ClientManagementResult result =
        handler.handle(
            "create",
            organizationIdentifier,
            tenant,
            operator,
            oAuthToken,
            request,
            requestAttributes,
            dryRun);

    if (result.hasException()) {
      AuditLog auditLog =
          AuditLogCreator.createOnError(
              "OrgClientManagementApi.create",
              tenant,
              operator,
              oAuthToken,
              result.getException(),
              requestAttributes);
      auditLogPublisher.publish(auditLog);
      return result.toResponse(dryRun);
    }

    AuditLog auditLog =
        AuditLogCreator.create(result.context());
    auditLogPublisher.publish(auditLog);

    return result.toResponse(dryRun);
  }

  @Override
  @Transaction(readOnly = true)
  public ClientManagementResponse findList(
      OrganizationIdentifier organizationIdentifier,
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      ClientQueries queries,
      RequestAttributes requestAttributes) {

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

    ClientManagementResult result =
        handler.handle(
            "findList",
            organizationIdentifier,
            tenant,
            operator,
            oAuthToken,
            queries,
            requestAttributes,
            false);

    AuditLog auditLog =
        AuditLogCreator.createOnRead(
            "OrgClientManagementApi.findList",
            "findList",
            tenant,
            operator,
            oAuthToken,
            requestAttributes);
    auditLogPublisher.publish(auditLog);

    return result.toResponse();
  }

  @Override
  @Transaction(readOnly = true)
  public ClientManagementResponse get(
      OrganizationIdentifier organizationIdentifier,
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      ClientIdentifier clientIdentifier,
      RequestAttributes requestAttributes) {

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

    ClientManagementResult result =
        handler.handle(
            "get",
            organizationIdentifier,
            tenant,
            operator,
            oAuthToken,
            clientIdentifier,
            requestAttributes,
            false);

    AuditLog auditLog =
        AuditLogCreator.createOnRead(
            "OrgClientManagementApi.get", "get", tenant, operator, oAuthToken, requestAttributes);
    auditLogPublisher.publish(auditLog);

    return result.toResponse();
  }

  @Override
  public ClientManagementResponse update(
      OrganizationIdentifier organizationIdentifier,
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      ClientIdentifier clientIdentifier,
      ClientRegistrationRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

    // Wrap request for handler
    ClientUpdateRequest updateRequest = new ClientUpdateRequest(clientIdentifier, request);

    ClientManagementResult result =
        handler.handle(
            "update",
            organizationIdentifier,
            tenant,
            operator,
            oAuthToken,
            updateRequest,
            requestAttributes,
            dryRun);

    if (result.hasException()) {
      AuditLog auditLog =
          AuditLogCreator.createOnError(
              "OrgClientManagementApi.update",
              tenant,
              operator,
              oAuthToken,
              result.getException(),
              requestAttributes);
      auditLogPublisher.publish(auditLog);
      return result.toResponse(dryRun);
    }

    AuditLog auditLog =
        AuditLogCreator.create(result.context());
    auditLogPublisher.publish(auditLog);

    return result.toResponse(dryRun);
  }

  @Override
  public ClientManagementResponse delete(
      OrganizationIdentifier organizationIdentifier,
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      ClientIdentifier clientIdentifier,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);
    ClientConfiguration clientConfiguration =
        clientConfigurationQueryRepository.findWithDisabled(tenant, clientIdentifier, true);

    ClientManagementResult result =
        handler.handle(
            "delete",
            organizationIdentifier,
            tenant,
            operator,
            oAuthToken,
            clientIdentifier,
            requestAttributes,
            dryRun);

    AuditLog auditLog =
        AuditLogCreator.createOnDeletion(
            "OrgClientManagementApi.delete",
            "delete",
            tenant,
            operator,
            oAuthToken,
            clientConfiguration.toMap(),
            requestAttributes);
    auditLogPublisher.publish(auditLog);

    return result.toResponse();
  }
}
