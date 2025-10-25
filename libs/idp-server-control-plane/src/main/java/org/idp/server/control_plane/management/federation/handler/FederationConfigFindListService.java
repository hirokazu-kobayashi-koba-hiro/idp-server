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

package org.idp.server.control_plane.management.federation.handler;

import java.util.List;
import java.util.Map;
import org.idp.server.control_plane.management.federation.FederationConfigManagementContextBuilder;
import org.idp.server.control_plane.management.federation.io.FederationConfigFindListRequest;
import org.idp.server.control_plane.management.federation.io.FederationConfigManagementResponse;
import org.idp.server.control_plane.management.federation.io.FederationConfigManagementStatus;
import org.idp.server.core.openid.federation.FederationConfiguration;
import org.idp.server.core.openid.federation.repository.FederationConfigurationQueryRepository;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Service for federation configuration list retrieval operations.
 *
 * <p>Handles business logic for retrieving lists of federation configurations. Part of
 * Handler/Service pattern.
 */
public class FederationConfigFindListService
    implements FederationConfigManagementService<FederationConfigFindListRequest> {

  private final FederationConfigurationQueryRepository queryRepository;

  public FederationConfigFindListService(FederationConfigurationQueryRepository queryRepository) {
    this.queryRepository = queryRepository;
  }

  @Override
  public FederationConfigManagementResponse execute(
      FederationConfigManagementContextBuilder builder,
      Tenant tenant,
      User operator,
      OAuthToken oAuthToken,
      FederationConfigFindListRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    long totalCount = queryRepository.findTotalCount(tenant, request.queries());
    if (totalCount == 0) {
      Map<String, Object> response =
          Map.of(
              "list", List.of(),
              "total_count", totalCount,
              "limit", request.queries().limit(),
              "offset", request.queries().offset());
      return new FederationConfigManagementResponse(FederationConfigManagementStatus.OK, response);
    }

    List<FederationConfiguration> configurations =
        queryRepository.findList(tenant, request.queries());

    Map<String, Object> response =
        Map.of(
            "list",
            configurations.stream().map(FederationConfiguration::toMap).toList(),
            "total_count",
            totalCount,
            "limit",
            request.queries().limit(),
            "offset",
            request.queries().offset());

    return new FederationConfigManagementResponse(FederationConfigManagementStatus.OK, response);
  }
}
