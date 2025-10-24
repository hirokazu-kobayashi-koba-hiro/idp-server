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
import org.idp.server.control_plane.management.authentication.interaction.OrgAuthenticationInteractionManagementApi;
import org.idp.server.control_plane.management.authentication.interaction.handler.*;
import org.idp.server.control_plane.management.authentication.interaction.io.AuthenticationInteractionFindListRequest;
import org.idp.server.control_plane.management.authentication.interaction.io.AuthenticationInteractionFindRequest;
import org.idp.server.control_plane.management.authentication.interaction.io.AuthenticationInteractionManagementResponse;
import org.idp.server.core.openid.authentication.AuthenticationTransactionIdentifier;
import org.idp.server.core.openid.authentication.interaction.AuthenticationInteractionQueries;
import org.idp.server.core.openid.authentication.repository.AuthenticationInteractionQueryRepository;
import org.idp.server.platform.audit.AuditLog;
import org.idp.server.platform.audit.AuditLogPublisher;
import org.idp.server.platform.datasource.Transaction;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantQueryRepository;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Organization-level authentication interaction management entry service.
 *
 * <p>This service implements organization-scoped authentication interaction management operations
 * that allow organization administrators to monitor authentication interactions within their
 * organization boundaries.
 *
 * <p>Organization-level operations follow the standard access control pattern:
 *
 * <ol>
 *   <li><strong>Organization access verification</strong> - Ensures the user has access to the
 *       organization
 *   <li><strong>Permission verification</strong> - Validates the user has necessary
 *       AUTHENTICATION_INTERACTION_READ permissions
 * </ol>
 *
 * <p>This service provides read-only access to authentication interaction data and comprehensive
 * audit logging for organization-level authentication interaction monitoring operations.
 *
 * @see OrgAuthenticationInteractionManagementApi
 * @see OrganizationAccessVerifier
 * @see
 *     org.idp.server.usecases.control_plane.system_manager.AuthenticationInteractionManagementEntryService
 */
@Transaction
public class OrgAuthenticationInteractionManagementEntryService
    implements OrgAuthenticationInteractionManagementApi {

  private final OrgAuthenticationInteractionManagementHandler handler;
  private final AuditLogPublisher auditLogPublisher;

  /**
   * Creates a new organization authentication interaction management entry service.
   *
   * @param tenantQueryRepository the tenant query repository
   * @param authenticationInteractionQueryRepository the authentication interaction query repository
   * @param auditLogPublisher the audit log publisher
   */
  public OrgAuthenticationInteractionManagementEntryService(
      TenantQueryRepository tenantQueryRepository,
      AuthenticationInteractionQueryRepository authenticationInteractionQueryRepository,
      AuditLogPublisher auditLogPublisher) {

    Map<String, AuthenticationInteractionManagementService<?>> services = new HashMap<>();
    services.put(
        "findList",
        new AuthenticationInteractionFindListService(authenticationInteractionQueryRepository));
    services.put(
        "get", new AuthenticationInteractionFindService(authenticationInteractionQueryRepository));

    this.handler =
        new OrgAuthenticationInteractionManagementHandler(
            services, this, tenantQueryRepository, new OrganizationAccessVerifier());
    this.auditLogPublisher = auditLogPublisher;
  }

  @Override
  @Transaction(readOnly = true)
  public AuthenticationInteractionManagementResponse findList(
      OrganizationAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      AuthenticationInteractionQueries queries,
      RequestAttributes requestAttributes) {

    AuthenticationInteractionFindListRequest findListRequest =
        new AuthenticationInteractionFindListRequest(queries);
    AuthenticationInteractionManagementResult result =
        handler.handle(
            "findList",
            authenticationContext,
            tenantIdentifier,
            findListRequest,
            requestAttributes);

    AuditLog auditLog = AuditLogCreator.create(result.context());
    auditLogPublisher.publish(auditLog);

    return result.toResponse();
  }

  @Override
  @Transaction(readOnly = true)
  public AuthenticationInteractionManagementResponse get(
      OrganizationAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      AuthenticationTransactionIdentifier identifier,
      String type,
      RequestAttributes requestAttributes) {

    AuthenticationInteractionFindRequest findRequest =
        new AuthenticationInteractionFindRequest(identifier, type);
    AuthenticationInteractionManagementResult result =
        handler.handle(
            "get", authenticationContext, tenantIdentifier, findRequest, requestAttributes);

    AuditLog auditLog = AuditLogCreator.create(result.context());
    auditLogPublisher.publish(auditLog);

    return result.toResponse();
  }
}
