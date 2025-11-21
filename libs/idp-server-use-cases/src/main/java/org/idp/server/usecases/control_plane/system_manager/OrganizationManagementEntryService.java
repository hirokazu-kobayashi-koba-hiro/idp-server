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
import java.util.Map;
import org.idp.server.control_plane.base.AdminAuthenticationContext;
import org.idp.server.control_plane.base.AuditLogCreator;
import org.idp.server.control_plane.management.organization.OrganizationManagementApi;
import org.idp.server.control_plane.management.organization.handler.OrganizationDeletionService;
import org.idp.server.control_plane.management.organization.handler.OrganizationFindService;
import org.idp.server.control_plane.management.organization.handler.OrganizationManagementHandler;
import org.idp.server.control_plane.management.organization.handler.OrganizationManagementResult;
import org.idp.server.control_plane.management.organization.handler.OrganizationManagementService;
import org.idp.server.control_plane.management.organization.io.OrganizationDeleteRequest;
import org.idp.server.control_plane.management.organization.io.OrganizationFindRequest;
import org.idp.server.control_plane.management.organization.io.OrganizationManagementResponse;
import org.idp.server.platform.audit.AuditLog;
import org.idp.server.platform.audit.AuditLogPublisher;
import org.idp.server.platform.datasource.Transaction;
import org.idp.server.platform.multi_tenancy.organization.OrganizationIdentifier;
import org.idp.server.platform.multi_tenancy.organization.OrganizationRepository;
import org.idp.server.platform.type.RequestAttributes;

@Transaction
public class OrganizationManagementEntryService implements OrganizationManagementApi {

  AuditLogPublisher auditLogPublisher;
  private OrganizationManagementHandler handler;

  public OrganizationManagementEntryService(
      OrganizationRepository organizationRepository, AuditLogPublisher auditLogPublisher) {
    this.auditLogPublisher = auditLogPublisher;

    // Create Handler
    this.handler = createHandler(organizationRepository);
  }

  private OrganizationManagementHandler createHandler(
      OrganizationRepository organizationRepository) {

    Map<String, OrganizationManagementService<?>> services = new HashMap<>();

    services.put("get", new OrganizationFindService(organizationRepository));

    services.put("delete", new OrganizationDeletionService(organizationRepository));

    return new OrganizationManagementHandler(services, this);
  }

  @Override
  @Transaction(readOnly = true)
  public OrganizationManagementResponse get(
      AdminAuthenticationContext authenticationContext,
      OrganizationIdentifier organizationIdentifier,
      RequestAttributes requestAttributes) {

    OrganizationManagementResult result =
        handler.handle(
            "get",
            authenticationContext,
            new OrganizationFindRequest(organizationIdentifier),
            requestAttributes,
            false);

    AuditLog auditLog = AuditLogCreator.create(result.context());
    auditLogPublisher.publish(auditLog);

    return result.toResponse(false);
  }

  @Override
  public OrganizationManagementResponse delete(
      AdminAuthenticationContext authenticationContext,
      OrganizationIdentifier organizationIdentifier,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    OrganizationManagementResult result =
        handler.handle(
            "delete",
            authenticationContext,
            new OrganizationDeleteRequest(organizationIdentifier),
            requestAttributes,
            dryRun);

    AuditLog auditLog = AuditLogCreator.create(result.context());
    auditLogPublisher.publish(auditLog);

    return result.toResponse(dryRun);
  }
}
