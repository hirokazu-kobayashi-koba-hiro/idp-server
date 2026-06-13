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

package org.idp.server.control_plane.management.identity.verification.result.handler;

import java.util.List;
import java.util.Map;
import org.idp.server.control_plane.management.identity.verification.result.IdentityVerificationResultManagementContextBuilder;
import org.idp.server.control_plane.management.identity.verification.result.io.IdentityVerificationResultFindListRequest;
import org.idp.server.control_plane.management.identity.verification.result.io.IdentityVerificationResultManagementResponse;
import org.idp.server.control_plane.management.identity.verification.result.io.IdentityVerificationResultManagementStatus;
import org.idp.server.core.extension.identity.verification.repository.IdentityVerificationResultQueryRepository;
import org.idp.server.core.extension.identity.verification.result.IdentityVerificationResult;
import org.idp.server.core.extension.identity.verification.result.IdentityVerificationResultQueries;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.type.RequestAttributes;

public class IdentityVerificationResultFindListService
    implements IdentityVerificationResultManagementService<
        IdentityVerificationResultFindListRequest> {

  private final IdentityVerificationResultQueryRepository queryRepository;

  public IdentityVerificationResultFindListService(
      IdentityVerificationResultQueryRepository queryRepository) {
    this.queryRepository = queryRepository;
  }

  @Override
  public IdentityVerificationResultManagementResponse execute(
      IdentityVerificationResultManagementContextBuilder builder,
      Tenant tenant,
      User operator,
      OAuthToken oAuthToken,
      IdentityVerificationResultFindListRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    IdentityVerificationResultQueries queries = request.queries();
    long totalCount = queryRepository.findTotalCount(tenant, queries);
    if (totalCount == 0) {
      Map<String, Object> response =
          Map.of(
              "list", List.of(),
              "total_count", totalCount,
              "limit", queries.limit(),
              "offset", queries.offset());
      return new IdentityVerificationResultManagementResponse(
          IdentityVerificationResultManagementStatus.OK, response);
    }

    List<IdentityVerificationResult> results = queryRepository.findList(tenant, queries);

    List<Map<String, Object>> list = new java.util.ArrayList<>();
    for (IdentityVerificationResult result : results) {
      list.add(result.toMap());
    }

    Map<String, Object> response =
        Map.of(
            "list",
            list,
            "total_count",
            totalCount,
            "limit",
            queries.limit(),
            "offset",
            queries.offset());

    return new IdentityVerificationResultManagementResponse(
        IdentityVerificationResultManagementStatus.OK, response);
  }
}
