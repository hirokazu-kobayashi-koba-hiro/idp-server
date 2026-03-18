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

package org.idp.server.control_plane.management.token.handler;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.idp.server.control_plane.management.token.TokenManagementContextBuilder;
import org.idp.server.control_plane.management.token.io.TokenFindListRequest;
import org.idp.server.control_plane.management.token.io.TokenManagementResponse;
import org.idp.server.control_plane.management.token.io.TokenManagementStatus;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthTokenQueries;
import org.idp.server.core.openid.token.repository.OAuthTokenManagementQueryRepository;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.type.RequestAttributes;

public class TokenFindListService implements TokenManagementService<TokenFindListRequest> {

  private final OAuthTokenManagementQueryRepository queryRepository;

  public TokenFindListService(OAuthTokenManagementQueryRepository queryRepository) {
    this.queryRepository = queryRepository;
  }

  @Override
  public TokenManagementResponse execute(
      TokenManagementContextBuilder builder,
      Tenant tenant,
      User operator,
      TokenFindListRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    OAuthTokenQueries queries = request.queries();
    long totalCount = queryRepository.findTotalCount(tenant, queries);
    if (totalCount == 0) {
      Map<String, Object> response =
          Map.of(
              "list", List.of(),
              "total_count", totalCount,
              "limit", queries.limit(),
              "offset", queries.offset());
      return new TokenManagementResponse(TokenManagementStatus.OK, response);
    }

    List<Map<String, String>> results = queryRepository.findList(tenant, queries);

    List<Map<String, Object>> list = new ArrayList<>();
    for (Map<String, String> row : results) {
      list.add(toTokenSummary(row));
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

    return new TokenManagementResponse(TokenManagementStatus.OK, response);
  }

  static Map<String, Object> toTokenSummary(Map<String, String> row) {
    Map<String, Object> summary = new LinkedHashMap<>();
    summary.put("id", row.get("id"));
    summary.put("tenant_id", row.get("tenant_id"));
    summary.put("user_id", row.get("user_id"));
    summary.put("client_id", row.get("client_id"));
    summary.put("grant_type", row.get("grant_type"));
    summary.put("scopes", row.get("scopes"));
    summary.put("token_type", row.get("token_type"));
    summary.put("access_token_expires_at", row.get("access_token_expires_at"));
    summary.put(
        "has_refresh_token",
        Objects.nonNull(row.get("hashed_refresh_token"))
            && !row.get("hashed_refresh_token").isEmpty());
    if (Objects.nonNull(row.get("refresh_token_expires_at"))
        && !row.get("refresh_token_expires_at").isEmpty()) {
      summary.put("refresh_token_expires_at", row.get("refresh_token_expires_at"));
    }
    summary.put("created_at", row.get("access_token_created_at"));
    return summary;
  }
}
