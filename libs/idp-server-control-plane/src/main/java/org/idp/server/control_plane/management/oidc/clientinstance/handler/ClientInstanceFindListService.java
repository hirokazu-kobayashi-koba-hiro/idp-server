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
import org.idp.server.control_plane.management.oidc.clientinstance.ClientInstanceManagementContextBuilder;
import org.idp.server.control_plane.management.oidc.clientinstance.io.ClientInstanceFindListRequest;
import org.idp.server.control_plane.management.oidc.clientinstance.io.ClientInstanceManagementResponse;
import org.idp.server.control_plane.management.oidc.clientinstance.io.ClientInstanceManagementStatus;
import org.idp.server.core.openid.clientinstance.ClientInstance;
import org.idp.server.core.openid.clientinstance.ClientInstanceQueryRepository;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.type.RequestAttributes;

/** Lists registered Client Instances of a client. */
public class ClientInstanceFindListService
    implements ClientInstanceManagementService<ClientInstanceFindListRequest> {

  private final ClientInstanceQueryRepository queryRepository;

  public ClientInstanceFindListService(ClientInstanceQueryRepository queryRepository) {
    this.queryRepository = queryRepository;
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

    List<ClientInstance> clientInstances =
        queryRepository.findList(
            tenant, request.requestedClientId(), request.limit(), request.offset());

    return new ClientInstanceManagementResponse(
        ClientInstanceManagementStatus.OK,
        Map.of(
            "list", clientInstances.stream().map(ClientInstance::toMap).toList(),
            "limit", request.limit(),
            "offset", request.offset()));
  }
}
