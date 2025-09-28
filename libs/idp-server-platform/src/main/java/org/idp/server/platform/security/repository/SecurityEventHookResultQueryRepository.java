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

package org.idp.server.platform.security.repository;

import java.util.List;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.security.hook.SecurityEventHookResult;
import org.idp.server.platform.security.hook.SecurityEventHookResultIdentifier;
import org.idp.server.platform.security.hook.SecurityEventHookResultQueries;

/**
 * Query repository for security event hook results.
 *
 * <p>Provides read operations for retrieving hook execution results, primarily used for retry
 * mechanisms and audit purposes.
 */
public interface SecurityEventHookResultQueryRepository {

  /**
   * Finds a security event hook result by identifier.
   *
   * @param tenant tenant context
   * @param identifier identifier of the hook result
   * @return hook result if found
   * @throws org.idp.server.platform.security.exception.SecurityEventHookResultNotFoundException if
   *     result not found
   */
  SecurityEventHookResult get(Tenant tenant, SecurityEventHookResultIdentifier identifier);

  /**
   * Finds a security event hook result by identifier.
   *
   * @param tenant tenant context
   * @param identifier identifier of the hook result
   * @return optional hook result
   */
  SecurityEventHookResult find(Tenant tenant, SecurityEventHookResultIdentifier identifier);

  /**
   * Finds hook results count for a tenant with query.
   *
   * @param tenant tenant context
   * @param queries query for result
   * @return long of hook results count
   */
  long findTotalCount(Tenant tenant, SecurityEventHookResultQueries queries);

  /**
   * Finds hook results for a tenant with query.
   *
   * @param tenant tenant context
   * @param queries query for result
   * @return list of hook results
   */
  List<SecurityEventHookResult> findList(Tenant tenant, SecurityEventHookResultQueries queries);
}
