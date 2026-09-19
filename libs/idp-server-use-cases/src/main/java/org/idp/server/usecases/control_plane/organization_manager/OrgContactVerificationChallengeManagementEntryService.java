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
import org.idp.server.control_plane.management.contact.OrgContactVerificationChallengeManagementApi;
import org.idp.server.control_plane.management.contact.handler.*;
import org.idp.server.control_plane.management.contact.io.ContactVerificationChallengeFindListRequest;
import org.idp.server.control_plane.management.contact.io.ContactVerificationChallengeFindRequest;
import org.idp.server.control_plane.management.contact.io.ContactVerificationChallengeManagementResponse;
import org.idp.server.core.openid.identity.contact.ContactVerificationChallengeIdentifier;
import org.idp.server.core.openid.identity.contact.ContactVerificationChallengeQueries;
import org.idp.server.core.openid.identity.contact.ContactVerificationChallengeRepository;
import org.idp.server.platform.audit.AuditLog;
import org.idp.server.platform.audit.AuditLogPublisher;
import org.idp.server.platform.datasource.Transaction;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantQueryRepository;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Organization-level contact verification challenge management entry service.
 *
 * <p>This service implements organization-scoped contact verification challenge management
 * operations that allow organization administrators to monitor contact verification challenges
 * within their organization boundaries.
 *
 * <p>Organization-level operations follow the standard access control pattern:
 *
 * <ol>
 *   <li><strong>Organization access verification</strong> - Ensures the user has access to the
 *       organization
 *   <li><strong>Permission verification</strong> - Validates the user has necessary
 *       CONTACT_VERIFICATION_CHALLENGE_READ permissions
 * </ol>
 *
 * <p>This service provides read-only access to contact verification challenge data and
 * comprehensive audit logging for organization-level contact verification challenge monitoring
 * operations.
 *
 * @see OrgContactVerificationChallengeManagementApi
 * @see OrganizationAccessVerifier
 * @see
 *     org.idp.server.usecases.control_plane.system_manager.ContactVerificationChallengeManagementEntryService
 */
@Transaction
public class OrgContactVerificationChallengeManagementEntryService
    implements OrgContactVerificationChallengeManagementApi {

  private final OrgContactVerificationChallengeManagementHandler handler;
  private final AuditLogPublisher auditLogPublisher;

  /**
   * Creates a new organization contact verification challenge management entry service.
   *
   * @param tenantQueryRepository the tenant query repository
   * @param contactVerificationChallengeRepository the contact verification challenge query
   *     repository
   * @param auditLogPublisher the audit log publisher
   */
  public OrgContactVerificationChallengeManagementEntryService(
      TenantQueryRepository tenantQueryRepository,
      ContactVerificationChallengeRepository contactVerificationChallengeRepository,
      AuditLogPublisher auditLogPublisher) {

    Map<String, ContactVerificationChallengeManagementService<?>> services = new HashMap<>();
    services.put(
        "findList",
        new ContactVerificationChallengeFindListService(contactVerificationChallengeRepository));
    services.put(
        "get", new ContactVerificationChallengeFindService(contactVerificationChallengeRepository));

    this.handler =
        new OrgContactVerificationChallengeManagementHandler(
            services, this, tenantQueryRepository, new OrganizationAccessVerifier());
    this.auditLogPublisher = auditLogPublisher;
  }

  @Override
  @Transaction(readOnly = true)
  public ContactVerificationChallengeManagementResponse findList(
      OrganizationAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      ContactVerificationChallengeQueries queries,
      RequestAttributes requestAttributes) {

    ContactVerificationChallengeFindListRequest findListRequest =
        new ContactVerificationChallengeFindListRequest(queries);
    ContactVerificationChallengeManagementResult result =
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
  public ContactVerificationChallengeManagementResponse get(
      OrganizationAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      ContactVerificationChallengeIdentifier identifier,
      RequestAttributes requestAttributes) {

    ContactVerificationChallengeFindRequest findRequest =
        new ContactVerificationChallengeFindRequest(identifier);
    ContactVerificationChallengeManagementResult result =
        handler.handle(
            "get", authenticationContext, tenantIdentifier, findRequest, requestAttributes);

    AuditLog auditLog = AuditLogCreator.create(result.context());
    auditLogPublisher.publish(auditLog);

    return result.toResponse();
  }
}
