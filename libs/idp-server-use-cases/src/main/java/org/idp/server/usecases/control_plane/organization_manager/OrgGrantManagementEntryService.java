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
import org.idp.server.control_plane.management.oidc.grant.OrgGrantManagementApi;
import org.idp.server.control_plane.management.oidc.grant.handler.GrantFindListService;
import org.idp.server.control_plane.management.oidc.grant.handler.GrantFindService;
import org.idp.server.control_plane.management.oidc.grant.handler.GrantManagementService;
import org.idp.server.control_plane.management.oidc.grant.handler.GrantRevocationService;
import org.idp.server.control_plane.management.oidc.grant.handler.OrgGrantManagementHandler;
import org.idp.server.control_plane.management.oidc.grant.io.GrantFindListRequest;
import org.idp.server.control_plane.management.oidc.grant.io.GrantFindRequest;
import org.idp.server.control_plane.management.oidc.grant.io.GrantManagementResponse;
import org.idp.server.control_plane.management.oidc.grant.io.GrantManagementResult;
import org.idp.server.control_plane.management.oidc.grant.io.GrantRevocationRequest;
import org.idp.server.core.openid.grant_management.AuthorizationGrantedIdentifier;
import org.idp.server.core.openid.grant_management.AuthorizationGrantedQueries;
import org.idp.server.core.openid.grant_management.AuthorizationGrantedQueryRepository;
import org.idp.server.core.openid.grant_management.AuthorizationGrantedRepository;
import org.idp.server.core.openid.token.repository.OAuthTokenCommandRepository;
import org.idp.server.platform.audit.AuditLog;
import org.idp.server.platform.audit.AuditLogPublisher;
import org.idp.server.platform.datasource.Transaction;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantQueryRepository;
import org.idp.server.platform.type.RequestAttributes;

@Transaction
public class OrgGrantManagementEntryService implements OrgGrantManagementApi {

  private final OrgGrantManagementHandler handler;
  private final AuditLogPublisher auditLogPublisher;

  public OrgGrantManagementEntryService(
      TenantQueryRepository tenantQueryRepository,
      AuthorizationGrantedQueryRepository authorizationGrantedQueryRepository,
      AuthorizationGrantedRepository authorizationGrantedRepository,
      OAuthTokenCommandRepository oAuthTokenCommandRepository,
      AuditLogPublisher auditLogPublisher) {

    Map<String, GrantManagementService<?>> services = new HashMap<>();
    services.put("findList", new GrantFindListService(authorizationGrantedQueryRepository));
    services.put("get", new GrantFindService(authorizationGrantedQueryRepository));
    services.put(
        "delete",
        new GrantRevocationService(
            authorizationGrantedQueryRepository,
            authorizationGrantedRepository,
            oAuthTokenCommandRepository));

    this.handler =
        new OrgGrantManagementHandler(
            services, this, tenantQueryRepository, new OrganizationAccessVerifier());
    this.auditLogPublisher = auditLogPublisher;
  }

  @Override
  @Transaction(readOnly = true)
  public GrantManagementResponse findList(
      OrganizationAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      AuthorizationGrantedQueries queries,
      RequestAttributes requestAttributes) {

    GrantFindListRequest request = new GrantFindListRequest(queries);
    GrantManagementResult result =
        handler.handle(
            "findList", authenticationContext, tenantIdentifier, request, requestAttributes, false);

    AuditLog auditLog = AuditLogCreator.create(result.context());
    auditLogPublisher.publish(auditLog);

    return result.toResponse(false);
  }

  @Override
  @Transaction(readOnly = true)
  public GrantManagementResponse get(
      OrganizationAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      AuthorizationGrantedIdentifier grantIdentifier,
      RequestAttributes requestAttributes) {

    GrantFindRequest request = new GrantFindRequest(grantIdentifier);
    GrantManagementResult result =
        handler.handle(
            "get", authenticationContext, tenantIdentifier, request, requestAttributes, false);

    AuditLog auditLog = AuditLogCreator.create(result.context());
    auditLogPublisher.publish(auditLog);

    return result.toResponse(false);
  }

  @Override
  public GrantManagementResponse delete(
      OrganizationAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      AuthorizationGrantedIdentifier grantIdentifier,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    GrantRevocationRequest request = new GrantRevocationRequest(grantIdentifier);
    GrantManagementResult result =
        handler.handle(
            "delete", authenticationContext, tenantIdentifier, request, requestAttributes, dryRun);

    AuditLog auditLog = AuditLogCreator.create(result.context());
    auditLogPublisher.publish(auditLog);

    return result.toResponse(dryRun);
  }
}
