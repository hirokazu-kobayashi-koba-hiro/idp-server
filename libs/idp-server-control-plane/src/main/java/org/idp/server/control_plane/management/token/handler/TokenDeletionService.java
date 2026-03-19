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

import java.util.Map;
import org.idp.server.control_plane.management.exception.ResourceNotFoundException;
import org.idp.server.control_plane.management.token.TokenManagementContextBuilder;
import org.idp.server.control_plane.management.token.io.TokenDeleteRequest;
import org.idp.server.control_plane.management.token.io.TokenManagementResponse;
import org.idp.server.control_plane.management.token.io.TokenManagementStatus;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthTokenIdentifier;
import org.idp.server.core.openid.token.repository.OAuthTokenManagementCommandRepository;
import org.idp.server.core.openid.token.repository.OAuthTokenManagementQueryRepository;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.type.RequestAttributes;

public class TokenDeletionService implements TokenManagementService<TokenDeleteRequest> {

  private final OAuthTokenManagementQueryRepository queryRepository;
  private final OAuthTokenManagementCommandRepository commandRepository;

  public TokenDeletionService(
      OAuthTokenManagementQueryRepository queryRepository,
      OAuthTokenManagementCommandRepository commandRepository) {
    this.queryRepository = queryRepository;
    this.commandRepository = commandRepository;
  }

  @Override
  public TokenManagementResponse execute(
      TokenManagementContextBuilder builder,
      Tenant tenant,
      User operator,
      TokenDeleteRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    OAuthTokenIdentifier identifier = request.identifier();
    Map<String, String> row = queryRepository.get(tenant, identifier);

    if (row == null || row.isEmpty()) {
      throw new ResourceNotFoundException("Token not found: " + identifier.value());
    }

    if (dryRun) {
      Map<String, Object> response =
          Map.of("dry_run", true, "target", TokenFindListService.toTokenSummary(row));
      return new TokenManagementResponse(TokenManagementStatus.OK, response);
    }

    commandRepository.delete(tenant, identifier);

    return new TokenManagementResponse(TokenManagementStatus.NO_CONTENT, Map.of());
  }
}
