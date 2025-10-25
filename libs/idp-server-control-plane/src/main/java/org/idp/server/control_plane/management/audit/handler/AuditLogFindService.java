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

package org.idp.server.control_plane.management.audit.handler;

import org.idp.server.control_plane.management.audit.AuditLogManagementContextBuilder;
import org.idp.server.control_plane.management.audit.io.AuditLogFindRequest;
import org.idp.server.control_plane.management.audit.io.AuditLogManagementResponse;
import org.idp.server.control_plane.management.audit.io.AuditLogManagementStatus;
import org.idp.server.control_plane.management.exception.ResourceNotFoundException;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.audit.AuditLog;
import org.idp.server.platform.audit.AuditLogQueryRepository;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Service for finding a single audit log.
 *
 * <p>Handles audit log retrieval logic.
 */
public class AuditLogFindService implements AuditLogManagementService<AuditLogFindRequest> {

  private final AuditLogQueryRepository auditLogQueryRepository;

  public AuditLogFindService(AuditLogQueryRepository auditLogQueryRepository) {
    this.auditLogQueryRepository = auditLogQueryRepository;
  }

  @Override
  public AuditLogManagementResponse execute(
      AuditLogManagementContextBuilder contextBuilder,
      Tenant tenant,
      User operator,
      OAuthToken oAuthToken,
      AuditLogFindRequest request,
      RequestAttributes requestAttributes) {

    AuditLog auditLog = auditLogQueryRepository.find(tenant, request.identifier());

    if (!auditLog.exists()) {
      throw new ResourceNotFoundException("Audit log not found: " + request.identifier().value());
    }

    // Update context builder with result (for audit logging)
    contextBuilder.withResult(auditLog);

    return new AuditLogManagementResponse(AuditLogManagementStatus.OK, auditLog.toMap());
  }
}
