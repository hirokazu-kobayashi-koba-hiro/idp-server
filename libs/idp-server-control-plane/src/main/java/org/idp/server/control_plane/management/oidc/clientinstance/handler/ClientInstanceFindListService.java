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

package org.idp.server.control_plane.management.oidc.clientinstance.handler;

import java.util.List;
import java.util.Map;
import org.idp.server.control_plane.management.exception.InvalidRequestException;
import org.idp.server.control_plane.management.oidc.clientinstance.ClientInstanceManagementContextBuilder;
import org.idp.server.control_plane.management.oidc.clientinstance.io.ClientInstanceFindListRequest;
import org.idp.server.control_plane.management.oidc.clientinstance.io.ClientInstanceManagementResponse;
import org.idp.server.control_plane.management.oidc.clientinstance.io.ClientInstanceManagementStatus;
import org.idp.server.core.openid.clientinstance.ClientInstance;
import org.idp.server.core.openid.clientinstance.ClientInstanceQueries;
import org.idp.server.core.openid.clientinstance.ClientInstanceQueryRepository;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfiguration;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfigurationQueryRepository;
import org.idp.server.core.openid.oauth.configuration.exception.ClientConfigurationNotFoundException;
import org.idp.server.core.openid.oauth.type.oauth.RequestedClientId;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.type.RequestAttributes;
import org.idp.server.platform.uuid.UuidMatcher;

/**
 * Searches the registered Client Instances of a tenant.
 *
 * <p>Conditions a value cannot satisfy are refused rather than matched against nothing: a {@code
 * status} of a typo would otherwise answer with an empty list, and read as "no such instance".
 */
public class ClientInstanceFindListService
    implements ClientInstanceManagementService<ClientInstanceFindListRequest> {

  private final ClientInstanceQueryRepository queryRepository;
  private final ClientConfigurationQueryRepository clientConfigurationQueryRepository;

  public ClientInstanceFindListService(
      ClientInstanceQueryRepository queryRepository,
      ClientConfigurationQueryRepository clientConfigurationQueryRepository) {
    this.queryRepository = queryRepository;
    this.clientConfigurationQueryRepository = clientConfigurationQueryRepository;
  }

  @Override
  public ClientInstanceManagementResponse execute(
      ClientInstanceManagementContextBuilder contextBuilder,
      Tenant tenant,
      User operator,
      OAuthToken oAuthToken,
      ClientInstanceFindListRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    ClientInstanceQueries queries = withClientIdentifier(tenant, request.queries());
    throwExceptionIfInvalidQueries(queries);

    long totalCount = queryRepository.findTotalCount(tenant, queries);
    List<ClientInstance> clientInstances =
        totalCount == 0 ? List.of() : queryRepository.findList(tenant, queries);

    return new ClientInstanceManagementResponse(
        ClientInstanceManagementStatus.OK,
        Map.of(
            "list", clientInstances.stream().map(ClientInstance::toMap).toList(),
            "total_count", totalCount,
            "limit", queries.limit(),
            "offset", queries.offset()));
  }

  /**
   * Instances record the client's identifier, while an operator may name the client by its alias.
   * An unknown client_id is kept as is and matches nothing.
   */
  private ClientInstanceQueries withClientIdentifier(Tenant tenant, ClientInstanceQueries queries) {
    if (!queries.hasClientId()) {
      return queries;
    }
    try {
      ClientConfiguration clientConfiguration =
          clientConfigurationQueryRepository.get(tenant, new RequestedClientId(queries.clientId()));
      return queries.withClientId(clientConfiguration.clientIdValue());
    } catch (ClientConfigurationNotFoundException e) {
      return queries;
    }
  }

  private void throwExceptionIfInvalidQueries(ClientInstanceQueries queries) {
    if (queries.hasUserId() && !UuidMatcher.isValid(queries.userId())) {
      throw new InvalidRequestException("user_id must be a UUID");
    }
    if (queries.hasStatus() && queries.status().isUndefined()) {
      throw new InvalidRequestException("status must be one of: active, revoked");
    }
    if (queries.hasRevocationReason() && !queries.revocationReason().exists()) {
      throw new InvalidRequestException("revocation_reason must be one of: operator, superseded");
    }
    try {
      if (queries.hasFrom()) queries.from();
      if (queries.hasTo()) queries.to();
    } catch (RuntimeException e) {
      throw new InvalidRequestException("from / to must be a date-time: " + e.getMessage());
    }
  }
}
