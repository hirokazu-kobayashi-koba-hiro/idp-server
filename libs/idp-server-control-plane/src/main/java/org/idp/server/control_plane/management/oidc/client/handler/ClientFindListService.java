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

package org.idp.server.control_plane.management.oidc.client.handler;

import java.util.List;
import java.util.Map;
import org.idp.server.control_plane.management.oidc.client.io.ClientManagementResponse;
import org.idp.server.control_plane.management.oidc.client.io.ClientManagementStatus;
import org.idp.server.control_plane.management.oidc.client.validator.ClientQueryValidator;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfiguration;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfigurationQueryRepository;
import org.idp.server.core.openid.oauth.configuration.client.ClientQueries;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Service for client list retrieval operations.
 *
 * <p>Handles business logic for retrieving lists of client configurations. Part of Handler/Service
 * pattern.
 */
public class ClientFindListService implements ClientManagementService<ClientQueries> {

  private final ClientConfigurationQueryRepository queryRepository;

  public ClientFindListService(ClientConfigurationQueryRepository queryRepository) {
    this.queryRepository = queryRepository;
  }

  @Override
  public ClientManagementResult execute(
      Tenant tenant,
      User operator,
      OAuthToken oAuthToken,
      ClientQueries queries,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    // Validation
    ClientQueryValidator validator = new ClientQueryValidator(queries);
    validator.validate(); // throws InvalidRequestException if invalid

    long totalCount = queryRepository.findTotalCount(tenant, queries);
    if (totalCount == 0) {
      Map<String, Object> response =
          Map.of(
              "list", List.of(),
              "total_count", totalCount,
              "limit", queries.limit(),
              "offset", queries.offset());
      return ClientManagementResult.success(
          tenant.identifier(), new ClientManagementResponse(ClientManagementStatus.OK, response));
    }

    List<ClientConfiguration> clientConfigurations = queryRepository.findList(tenant, queries);

    Map<String, Object> response =
        Map.of(
            "list",
            clientConfigurations.stream().map(ClientConfiguration::toMap).toList(),
            "total_count",
            totalCount,
            "limit",
            queries.limit(),
            "offset",
            queries.offset());

    return ClientManagementResult.success(
        tenant.identifier(), new ClientManagementResponse(ClientManagementStatus.OK, response));
  }
}
