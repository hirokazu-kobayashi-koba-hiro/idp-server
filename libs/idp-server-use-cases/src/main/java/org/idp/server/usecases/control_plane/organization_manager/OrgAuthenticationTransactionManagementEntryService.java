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
import org.idp.server.control_plane.management.authentication.transaction.OrgAuthenticationTransactionManagementApi;
import org.idp.server.control_plane.management.authentication.transaction.handler.AuthenticationTransactionFindListService;
import org.idp.server.control_plane.management.authentication.transaction.handler.AuthenticationTransactionFindService;
import org.idp.server.control_plane.management.authentication.transaction.handler.AuthenticationTransactionManagementResult;
import org.idp.server.control_plane.management.authentication.transaction.handler.AuthenticationTransactionManagementService;
import org.idp.server.control_plane.management.authentication.transaction.handler.OrgAuthenticationTransactionManagementHandler;
import org.idp.server.control_plane.management.authentication.transaction.io.AuthenticationTransactionManagementResponse;
import org.idp.server.core.openid.authentication.AuthenticationTransactionIdentifier;
import org.idp.server.core.openid.authentication.AuthenticationTransactionQueries;
import org.idp.server.core.openid.authentication.repository.AuthenticationTransactionQueryRepository;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.audit.AuditLog;
import org.idp.server.platform.audit.AuditLogPublisher;
import org.idp.server.platform.datasource.Transaction;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.organization.OrganizationIdentifier;
import org.idp.server.platform.multi_tenancy.organization.OrganizationRepository;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantQueryRepository;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Organization-level authentication transaction management entry service.
 *
 * <p>This service implements organization-scoped authentication transaction management operations
 * that allow organization administrators to monitor authentication transactions within their
 * organization boundaries.
 *
 * <p>Organization-level operations follow the standard access control pattern:
 *
 * <ol>
 *   <li><strong>Organization access verification</strong> - Ensures the user has access to the
 *       organization
 *   <li><strong>Permission verification</strong> - Validates the user has necessary
 *       AUTHENTICATION_TRANSACTION_READ permissions
 * </ol>
 *
 * <p>This service provides read-only access to authentication transaction data and comprehensive
 * audit logging for organization-level authentication transaction monitoring operations.
 *
 * @see OrgAuthenticationTransactionManagementApi
 * @see OrgAuthenticationTransactionManagementHandler
 * @see
 *     org.idp.server.usecases.control_plane.system_manager.AuthenticationTransactionManagementEntryService
 */
@Transaction
public class OrgAuthenticationTransactionManagementEntryService
    implements OrgAuthenticationTransactionManagementApi {

  AuditLogPublisher auditLogPublisher;
  TenantQueryRepository tenantQueryRepository;
  LoggerWrapper log =
      LoggerWrapper.getLogger(OrgAuthenticationTransactionManagementEntryService.class);

  // Handler/Service pattern
  private OrgAuthenticationTransactionManagementHandler handler;

  /**
   * Creates a new organization authentication transaction management entry service.
   *
   * @param authenticationTransactionQueryRepository the authentication transaction query repository
   * @param tenantQueryRepository the tenant query repository
   * @param organizationRepository the organization repository
   * @param auditLogPublisher the audit log publisher
   */
  public OrgAuthenticationTransactionManagementEntryService(
      AuthenticationTransactionQueryRepository authenticationTransactionQueryRepository,
      TenantQueryRepository tenantQueryRepository,
      OrganizationRepository organizationRepository,
      AuditLogPublisher auditLogPublisher) {
    this.auditLogPublisher = auditLogPublisher;
    this.tenantQueryRepository = tenantQueryRepository;

    this.handler =
        createHandler(
            authenticationTransactionQueryRepository,
            tenantQueryRepository,
            organizationRepository);
  }

  private OrgAuthenticationTransactionManagementHandler createHandler(
      AuthenticationTransactionQueryRepository authenticationTransactionQueryRepository,
      TenantQueryRepository tenantQueryRepository,
      OrganizationRepository organizationRepository) {

    Map<String, AuthenticationTransactionManagementService<?>> services = new HashMap<>();
    services.put(
        "findList",
        new AuthenticationTransactionFindListService(authenticationTransactionQueryRepository));
    services.put(
        "get", new AuthenticationTransactionFindService(authenticationTransactionQueryRepository));

    return new OrgAuthenticationTransactionManagementHandler(
        services, this, tenantQueryRepository, organizationRepository);
  }

  @Override
  @Transaction(readOnly = true)
  public AuthenticationTransactionManagementResponse findList(
      OrganizationIdentifier organizationIdentifier,
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      AuthenticationTransactionQueries queries,
      RequestAttributes requestAttributes) {

    // Delegate to Handler/Service pattern
    AuthenticationTransactionManagementResult result =
        handler.handle(
            "findList",
            organizationIdentifier,
            tenantIdentifier,
            operator,
            oAuthToken,
            queries,
            requestAttributes,
            false);

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

    if (result.hasException()) {
      AuditLog auditLog =
          AuditLogCreator.createOnError(
              "OrgAuthenticationTransactionManagementApi.findList",
              tenant,
              operator,
              oAuthToken,
              result.getException(),
              requestAttributes);
      auditLogPublisher.publish(auditLog);
      return result.toResponse(false);
    }

    // Record audit log (read operation)
    AuditLog auditLog =
        AuditLogCreator.createOnRead(
            "OrgAuthenticationTransactionManagementApi.findList",
            "findList",
            tenant,
            operator,
            oAuthToken,
            requestAttributes);
    auditLogPublisher.publish(auditLog);

    return result.toResponse(false);
  }

  @Override
  @Transaction(readOnly = true)
  public AuthenticationTransactionManagementResponse get(
      OrganizationIdentifier organizationIdentifier,
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      AuthenticationTransactionIdentifier identifier,
      RequestAttributes requestAttributes) {

    // Delegate to Handler/Service pattern
    AuthenticationTransactionManagementResult result =
        handler.handle(
            "get",
            organizationIdentifier,
            tenantIdentifier,
            operator,
            oAuthToken,
            identifier,
            requestAttributes,
            false);

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

    if (result.hasException()) {
      AuditLog auditLog =
          AuditLogCreator.createOnError(
              "OrgAuthenticationTransactionManagementApi.get",
              tenant,
              operator,
              oAuthToken,
              result.getException(),
              requestAttributes);
      auditLogPublisher.publish(auditLog);
      return result.toResponse(false);
    }

    // Record audit log (read operation)
    AuditLog auditLog =
        AuditLogCreator.createOnRead(
            "OrgAuthenticationTransactionManagementApi.get",
            "get",
            tenant,
            operator,
            oAuthToken,
            requestAttributes);
    auditLogPublisher.publish(auditLog);

    return result.toResponse(false);
  }
}
