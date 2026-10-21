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
package org.idp.server.account_linking.repository;

import org.idp.server.platform.multi_tenancy.tenant.Tenant;

/**
 * Bulk cleanup of linking sessions, for the system administrator's expired-data deletion.
 *
 * <p>Works across tenants, like the other expired-data deletions. A session past its expiry can no
 * longer be started, called back or completed; a parked one still holds encrypted external tokens
 * that nobody can claim. They are deleted without being revoked at the external IdP: once the row
 * is gone nobody holds those tokens, and revoking would mean an external call per row inside one
 * bulk transaction.
 */
public interface AccountLinkingSessionOperationCommandRepository {

  /**
   * @param tenant the administrator's tenant the operation runs under
   * @param limit the most rows deleted in one call
   * @return the rows deleted
   */
  int deleteExpired(Tenant tenant, int limit);
}
