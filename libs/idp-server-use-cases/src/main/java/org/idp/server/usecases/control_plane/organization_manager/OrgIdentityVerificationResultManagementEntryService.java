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
import org.idp.server.control_plane.management.identity.verification.result.OrgIdentityVerificationResultManagementApi;
import org.idp.server.control_plane.management.identity.verification.result.handler.*;
import org.idp.server.control_plane.management.identity.verification.result.io.*;
import org.idp.server.core.extension.identity.verification.repository.IdentityVerificationResultQueryRepository;
import org.idp.server.core.extension.identity.verification.result.IdentityVerificationResultIdentifier;
import org.idp.server.core.extension.identity.verification.result.IdentityVerificationResultQueries;
import org.idp.server.platform.audit.AuditLog;
import org.idp.server.platform.audit.AuditLogPublisher;
import org.idp.server.platform.datasource.Transaction;
import org.idp.server.platform.multi_tenancy.organization.OrganizationRepository;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantQueryRepository;
import org.idp.server.platform.type.RequestAttributes;

@Transaction
public class OrgIdentityVerificationResultManagementEntryService
    implements OrgIdentityVerificationResultManagementApi {

  private final OrgIdentityVerificationResultManagementHandler handler;
  private final AuditLogPublisher auditLogPublisher;

  public OrgIdentityVerificationResultManagementEntryService(
      TenantQueryRepository tenantQueryRepository,
      OrganizationRepository organizationRepository,
      IdentityVerificationResultQueryRepository identityVerificationResultQueryRepository,
      AuditLogPublisher auditLogPublisher) {

    Map<String, IdentityVerificationResultManagementService<?>> services = new HashMap<>();
    services.put(
        "findList",
        new IdentityVerificationResultFindListService(identityVerificationResultQueryRepository));
    services.put(
        "get",
        new IdentityVerificationResultFindService(identityVerificationResultQueryRepository));

    this.handler =
        new OrgIdentityVerificationResultManagementHandler(
            services,
            this,
            tenantQueryRepository,
            organizationRepository,
            new OrganizationAccessVerifier());
    this.auditLogPublisher = auditLogPublisher;
  }

  @Override
  @Transaction(readOnly = true)
  public IdentityVerificationResultManagementResponse findList(
      OrganizationAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      IdentityVerificationResultQueries queries,
      RequestAttributes requestAttributes) {

    IdentityVerificationResultFindListRequest findListRequest =
        new IdentityVerificationResultFindListRequest(queries);
    IdentityVerificationResultManagementResult result =
        handler.handle(
            "findList",
            authenticationContext,
            tenantIdentifier,
            findListRequest,
            requestAttributes,
            false);

    AuditLog auditLog = AuditLogCreator.create(result.context());
    auditLogPublisher.publish(auditLog);

    return result.toResponse(false);
  }

  @Override
  @Transaction(readOnly = true)
  public IdentityVerificationResultManagementResponse get(
      OrganizationAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      IdentityVerificationResultIdentifier identifier,
      RequestAttributes requestAttributes) {

    IdentityVerificationResultFindRequest findRequest =
        new IdentityVerificationResultFindRequest(identifier);
    IdentityVerificationResultManagementResult result =
        handler.handle(
            "get", authenticationContext, tenantIdentifier, findRequest, requestAttributes, false);

    AuditLog auditLog = AuditLogCreator.create(result.context());
    auditLogPublisher.publish(auditLog);

    return result.toResponse(false);
  }
}
