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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.control_plane.management.audit.io.AuditLogManagementResponse;
import org.idp.server.control_plane.management.audit.io.AuditLogManagementStatus;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.audit.AuditLog;
import org.idp.server.platform.audit.AuditLogQueries;
import org.idp.server.platform.audit.AuditLogQueryRepository;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Service for retrieving paginated audit log list.
 *
 * <p>Handles audit log list retrieval logic with pagination support.
 *
 * <h2>Responsibilities</h2>
 *
 * <ul>
 *   <li>Query total count of matching audit logs
 *   <li>Retrieve paginated audit log list
 *   <li>Format response with pagination metadata
 * </ul>
 */
public class AuditLogFindListService implements AuditLogManagementService<AuditLogFindListRequest> {

  private final AuditLogQueryRepository auditLogQueryRepository;

  public AuditLogFindListService(AuditLogQueryRepository auditLogQueryRepository) {
    this.auditLogQueryRepository = auditLogQueryRepository;
  }

  @Override
  public AuditLogManagementResult execute(
      Tenant tenant,
      User operator,
      OAuthToken oAuthToken,
      AuditLogFindListRequest request,
      RequestAttributes requestAttributes) {

    AuditLogQueries queries = request.queries();

    // 1. Query total count
    long totalCount = auditLogQueryRepository.findTotalCount(tenant, queries);

    // 2. Early return if no results
    if (totalCount == 0) {
      Map<String, Object> response = new HashMap<>();
      response.put("list", List.of());
      response.put("total_count", 0);
      response.put("limit", queries.limit());
      response.put("offset", queries.offset());
      return AuditLogManagementResult.success(
          tenant, new AuditLogManagementResponse(AuditLogManagementStatus.OK, response));
    }

    // 3. Retrieve paginated list
    List<AuditLog> auditLogs = auditLogQueryRepository.findList(tenant, queries);

    // 4. Format response
    Map<String, Object> response = new HashMap<>();
    response.put("list", auditLogs.stream().map(AuditLog::toMap).toList());
    response.put("total_count", totalCount);
    response.put("limit", queries.limit());
    response.put("offset", queries.offset());

    return AuditLogManagementResult.success(
        tenant, new AuditLogManagementResponse(AuditLogManagementStatus.OK, response));
  }
}
