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

package org.idp.server.control_plane.management.oidc.grant.handler;

import java.util.List;
import java.util.Map;
import org.idp.server.control_plane.management.oidc.grant.GrantManagementContextBuilder;
import org.idp.server.control_plane.management.oidc.grant.io.GrantFindListRequest;
import org.idp.server.control_plane.management.oidc.grant.io.GrantManagementResponse;
import org.idp.server.control_plane.management.oidc.grant.io.GrantManagementStatus;
import org.idp.server.core.openid.grant_management.AuthorizationGranted;
import org.idp.server.core.openid.grant_management.AuthorizationGrantedQueryRepository;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Service for grant list retrieval operations.
 *
 * <p>Handles business logic for retrieving lists of authorization grants. Part of Handler/Service
 * pattern.
 */
public class GrantFindListService implements GrantManagementService<GrantFindListRequest> {

  private final AuthorizationGrantedQueryRepository queryRepository;

  public GrantFindListService(AuthorizationGrantedQueryRepository queryRepository) {
    this.queryRepository = queryRepository;
  }

  @Override
  public GrantManagementResponse execute(
      GrantManagementContextBuilder contextBuilder,
      Tenant tenant,
      User operator,
      OAuthToken oAuthToken,
      GrantFindListRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    long totalCount = queryRepository.findTotalCount(tenant, request.queries());
    if (totalCount == 0) {
      Map<String, Object> response =
          Map.of(
              "list",
              List.of(),
              "total_count",
              totalCount,
              "limit",
              request.queries().limit(),
              "offset",
              request.queries().offset());
      return new GrantManagementResponse(GrantManagementStatus.OK, response);
    }

    List<AuthorizationGranted> grants = queryRepository.findList(tenant, request.queries());

    Map<String, Object> response =
        Map.of(
            "list",
            grants.stream().map(AuthorizationGranted::toMap).toList(),
            "total_count",
            totalCount,
            "limit",
            request.queries().limit(),
            "offset",
            request.queries().offset());

    return new GrantManagementResponse(GrantManagementStatus.OK, response);
  }
}
