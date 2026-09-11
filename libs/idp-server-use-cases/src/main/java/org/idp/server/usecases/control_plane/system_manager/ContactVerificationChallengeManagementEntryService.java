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
import org.idp.server.control_plane.management.contact.ContactVerificationChallengeManagementApi;
import org.idp.server.control_plane.management.contact.handler.*;
import org.idp.server.control_plane.management.contact.io.ContactVerificationChallengeFindListRequest;
import org.idp.server.control_plane.management.contact.io.ContactVerificationChallengeFindRequest;
import org.idp.server.control_plane.management.contact.io.ContactVerificationChallengeManagementResponse;
import org.idp.server.core.openid.identity.contact.ContactVerificationChallengeIdentifier;
import org.idp.server.core.openid.identity.contact.ContactVerificationChallengeQueries;
import org.idp.server.core.openid.identity.contact.ContactVerificationChallengeRepository;
import org.idp.server.platform.audit.*;
import org.idp.server.platform.datasource.Transaction;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantQueryRepository;
import org.idp.server.platform.type.RequestAttributes;

@Transaction
public class ContactVerificationChallengeManagementEntryService
    implements ContactVerificationChallengeManagementApi {

  private final ContactVerificationChallengeManagementHandler handler;
  private final AuditLogPublisher auditLogPublisher;

  public ContactVerificationChallengeManagementEntryService(
      ContactVerificationChallengeRepository contactVerificationChallengeRepository,
      TenantQueryRepository tenantQueryRepository,
      AuditLogPublisher auditLogPublisher) {

    Map<String, ContactVerificationChallengeManagementService<?>> services = new HashMap<>();
    services.put(
        "findList",
        new ContactVerificationChallengeFindListService(contactVerificationChallengeRepository));
    services.put(
        "get", new ContactVerificationChallengeFindService(contactVerificationChallengeRepository));

    this.handler =
        new ContactVerificationChallengeManagementHandler(services, this, tenantQueryRepository);
    this.auditLogPublisher = auditLogPublisher;
  }

  @Override
  @Transaction(readOnly = true)
  public ContactVerificationChallengeManagementResponse findList(
      AdminAuthenticationContext authenticationContext,
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
      AdminAuthenticationContext authenticationContext,
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
