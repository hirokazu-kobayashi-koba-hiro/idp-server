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
import org.idp.server.control_plane.base.OrganizationAccessVerifier;
import org.idp.server.control_plane.base.OrganizationAuthenticationContext;
import org.idp.server.control_plane.management.oidc.authorization.OrgAuthorizationServerManagementApi;
import org.idp.server.control_plane.management.oidc.authorization.handler.*;
import org.idp.server.control_plane.management.oidc.authorization.io.AuthorizationServerFindRequest;
import org.idp.server.control_plane.management.oidc.authorization.io.AuthorizationServerManagementResponse;
import org.idp.server.control_plane.management.oidc.authorization.io.AuthorizationServerUpdateRequest;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfigurationCommandRepository;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfigurationQueryRepository;
import org.idp.server.platform.audit.AuditLog;
import org.idp.server.platform.audit.AuditLogPublisher;
import org.idp.server.platform.datasource.Transaction;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantQueryRepository;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Organization-level authorization server management entry service.
 *
 * <p>This service implements organization-scoped authorization server configuration management
 * operations that allow organization administrators to manage authorization server settings within
 * their organization boundaries.
 *
 * <p>Organization-level operations follow the standard access control pattern:
 *
 * <ol>
 *   <li><strong>Organization access verification</strong> - Ensures the user has access to the
 *       organization
 *   <li><strong>Permission verification</strong> - Validates the user has necessary
 *       TENANT_READ/TENANT_UPDATE permissions
 * </ol>
 *
 * <p>This service provides authorization server configuration retrieval and update operations with
 * comprehensive audit logging for organization-level authorization server management.
 *
 * @see OrgAuthorizationServerManagementApi
 * @see OrganizationAccessVerifier
 * @see
 *     org.idp.server.usecases.control_plane.system_manager.AuthorizationServerManagementEntryService
 */
@Transaction
public class OrgAuthorizationServerManagementEntryService
    implements OrgAuthorizationServerManagementApi {

  private final OrgAuthorizationServerManagementHandler handler;
  private final TenantQueryRepository tenantQueryRepository;
  private final AuditLogPublisher auditLogPublisher;

  /**
   * Creates a new organization authorization server management entry service.
   *
   * @param tenantQueryRepository the tenant query repository
   * @param queryRepository the authorization server configuration query repository
   * @param commandRepository the authorization server configuration command repository
   * @param auditLogPublisher the audit log publisher
   */
  public OrgAuthorizationServerManagementEntryService(
      TenantQueryRepository tenantQueryRepository,
      AuthorizationServerConfigurationQueryRepository queryRepository,
      AuthorizationServerConfigurationCommandRepository commandRepository,
      AuditLogPublisher auditLogPublisher) {

    Map<String, AuthorizationServerManagementService<?>> services = new HashMap<>();
    services.put("get", new AuthorizationServerFindService(queryRepository));
    services.put(
        "update", new AuthorizationServerUpdateService(queryRepository, commandRepository));

    this.handler =
        new OrgAuthorizationServerManagementHandler(
            services, this, tenantQueryRepository, new OrganizationAccessVerifier());

    this.tenantQueryRepository = tenantQueryRepository;
    this.auditLogPublisher = auditLogPublisher;
  }

  @Override
  @Transaction(readOnly = true)
  public AuthorizationServerManagementResponse get(
      OrganizationAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      RequestAttributes requestAttributes) {

    AuthorizationServerFindRequest findRequest = new AuthorizationServerFindRequest();
    AuthorizationServerManagementResult result =
        handler.handle(
            "get", authenticationContext, tenantIdentifier, findRequest, requestAttributes, false);

    AuditLog auditLog = AuditLogCreator.create(result.context());
    auditLogPublisher.publish(auditLog);

    return result.toResponse(false);
  }

  @Override
  public AuthorizationServerManagementResponse update(
      OrganizationAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      AuthorizationServerUpdateRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    AuthorizationServerManagementResult result =
        handler.handle(
            "update", authenticationContext, tenantIdentifier, request, requestAttributes, dryRun);

    AuditLog auditLog = AuditLogCreator.create(result.context());
    auditLogPublisher.publish(auditLog);

    return result.toResponse(dryRun);
  }
}
