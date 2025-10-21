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

import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Service interface for audit log management operations.
 *
 * <p>This interface defines the contract for audit log management services that handle specific
 * operations (findList, get) in the Handler/Service pattern.
 *
 * <h2>Responsibilities</h2>
 *
 * <ul>
 *   <li>Execute business logic for specific audit log operation
 *   <li>Query audit logs from repository
 *   <li>Return success result or throw ManagementApiException
 * </ul>
 *
 * <h2>NOT Responsibilities</h2>
 *
 * <ul>
 *   <li>Permission verification (handled by Handler)
 *   <li>Tenant retrieval (handled by Handler)
 *   <li>Audit logging (handled by EntryService)
 *   <li>Transaction management (handled by EntryService)
 * </ul>
 *
 * @param <REQUEST> the request type for this service
 * @see AuditLogManagementResult
 * @see AuditLogManagementHandler
 */
public interface AuditLogManagementService<REQUEST> {

  /**
   * Executes the audit log management operation.
   *
   * @param tenant the tenant context (already retrieved by Handler)
   * @param operator the user performing the operation (already permission-verified)
   * @param oAuthToken the OAuth token for the operation
   * @param request the operation-specific request object
   * @param requestAttributes HTTP request attributes for audit logging
   * @return AuditLogManagementResult containing operation outcome
   */
  AuditLogManagementResult execute(
      Tenant tenant,
      User operator,
      OAuthToken oAuthToken,
      REQUEST request,
      RequestAttributes requestAttributes);
}
