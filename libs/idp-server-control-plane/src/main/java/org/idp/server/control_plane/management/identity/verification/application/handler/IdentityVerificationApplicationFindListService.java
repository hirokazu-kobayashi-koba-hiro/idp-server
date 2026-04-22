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

package org.idp.server.control_plane.management.identity.verification.application.handler;

import java.util.List;
import java.util.Map;
import org.idp.server.control_plane.management.identity.verification.application.IdentityVerificationApplicationManagementContextBuilder;
import org.idp.server.control_plane.management.identity.verification.application.io.IdentityVerificationApplicationFindListRequest;
import org.idp.server.control_plane.management.identity.verification.application.io.IdentityVerificationApplicationManagementResponse;
import org.idp.server.control_plane.management.identity.verification.application.io.IdentityVerificationApplicationManagementStatus;
import org.idp.server.core.extension.identity.verification.application.model.IdentityVerificationApplication;
import org.idp.server.core.extension.identity.verification.application.model.IdentityVerificationApplicationQueries;
import org.idp.server.core.extension.identity.verification.application.model.IdentityVerificationApplications;
import org.idp.server.core.extension.identity.verification.repository.IdentityVerificationApplicationQueryRepository;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.type.RequestAttributes;

public class IdentityVerificationApplicationFindListService
    implements IdentityVerificationApplicationManagementService<
        IdentityVerificationApplicationFindListRequest> {

  private final IdentityVerificationApplicationQueryRepository queryRepository;

  public IdentityVerificationApplicationFindListService(
      IdentityVerificationApplicationQueryRepository queryRepository) {
    this.queryRepository = queryRepository;
  }

  @Override
  public IdentityVerificationApplicationManagementResponse execute(
      IdentityVerificationApplicationManagementContextBuilder builder,
      Tenant tenant,
      User operator,
      OAuthToken oAuthToken,
      IdentityVerificationApplicationFindListRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    IdentityVerificationApplicationQueries queries = request.queries();
    long totalCount = queryRepository.findTotalCount(tenant, queries);
    if (totalCount == 0) {
      Map<String, Object> response =
          Map.of(
              "list", List.of(),
              "total_count", totalCount,
              "limit", queries.limit(),
              "offset", queries.offset());
      return new IdentityVerificationApplicationManagementResponse(
          IdentityVerificationApplicationManagementStatus.OK, response);
    }

    IdentityVerificationApplications applications = queryRepository.findList(tenant, queries);

    List<Map<String, Object>> list = new java.util.ArrayList<>();
    for (IdentityVerificationApplication application : applications) {
      list.add(application.toMap());
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

    return new IdentityVerificationApplicationManagementResponse(
        IdentityVerificationApplicationManagementStatus.OK, response);
  }
}
