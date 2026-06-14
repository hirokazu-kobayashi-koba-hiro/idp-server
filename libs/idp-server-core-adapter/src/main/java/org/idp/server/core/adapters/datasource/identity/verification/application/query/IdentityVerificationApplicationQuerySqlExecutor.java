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

package org.idp.server.core.adapters.datasource.identity.verification.application.query;

import java.util.List;
import java.util.Map;
import org.idp.server.core.extension.identity.verification.application.history.HistoryQueryPlan;
import org.idp.server.core.extension.identity.verification.application.model.IdentityVerificationApplicationIdentifier;
import org.idp.server.core.extension.identity.verification.application.model.IdentityVerificationApplicationQueries;
import org.idp.server.core.extension.identity.verification.application.model.IdentityVerificationExternalApplicationIdentifier;
import org.idp.server.core.openid.identity.User;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public interface IdentityVerificationApplicationQuerySqlExecutor {
  Map<String, String> selectOne(
      Tenant tenant, User user, IdentityVerificationApplicationIdentifier identifier);

  Map<String, String> selectOne(
      Tenant tenant, IdentityVerificationApplicationIdentifier identifier);

  /**
   * Fast path: lookup by the dedicated {@code external_application_id} column (B-tree indexed).
   * Returns empty / null if the row pre-dates the backfill, in which case callers should fall back
   * to {@link #selectOneByDetail(Tenant, String, String)}.
   */
  Map<String, String> selectOneByExternalApplicationId(
      Tenant tenant, IdentityVerificationExternalApplicationIdentifier externalApplicationId);

  /**
   * Legacy / fallback path: lookup by {@code application_details ->> key = value}. Kept for
   * pre-backfill rows and as a safety net for rollbacks. The GIN index on {@code
   * application_details} is not actually used due to RLS + LEAKPROOF; treat this as a slow path.
   */
  Map<String, String> selectOneByDetail(Tenant tenant, String key, String identifier);

  List<Map<String, String>> selectList(Tenant tenant, User user);

  List<Map<String, String>> selectList(
      Tenant tenant, User user, IdentityVerificationApplicationQueries queries);

  Map<String, String> selectCount(
      Tenant tenant, User user, IdentityVerificationApplicationQueries queries);

  List<Map<String, String>> selectList(
      Tenant tenant, IdentityVerificationApplicationQueries queries);

  Map<String, String> selectCount(Tenant tenant, IdentityVerificationApplicationQueries queries);

  /**
   * Materialize the rows matching observations declared by {@code plan} as a single SQL statement.
   * Callers must check {@link HistoryQueryPlan#isEmpty()} before invoking — implementations may
   * assume the plan declares at least one observation.
   */
  List<Map<String, String>> selectHistory(Tenant tenant, User user, HistoryQueryPlan plan);
}
