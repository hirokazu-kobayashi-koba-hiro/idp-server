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

package org.idp.server.control_plane.management.authentication.interaction.handler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.control_plane.management.authentication.interaction.AuthenticationInteractionManagementContextBuilder;
import org.idp.server.control_plane.management.authentication.interaction.io.AuthenticationInteractionFindListRequest;
import org.idp.server.control_plane.management.authentication.interaction.io.AuthenticationInteractionManagementResponse;
import org.idp.server.control_plane.management.authentication.interaction.io.AuthenticationInteractionManagementStatus;
import org.idp.server.core.openid.authentication.interaction.AuthenticationInteraction;
import org.idp.server.core.openid.authentication.repository.AuthenticationInteractionQueryRepository;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Service for finding list of authentication interactions.
 *
 * <p>Handles authentication interaction list retrieval logic with pagination support.
 *
 * <h2>Responsibilities</h2>
 *
 * <ul>
 *   <li>Query repository for total count
 *   <li>Query repository for authentication interaction list
 *   <li>Build paginated response
 * </ul>
 */
public class AuthenticationInteractionFindListService
    implements AuthenticationInteractionManagementService<
        AuthenticationInteractionFindListRequest> {

  private final AuthenticationInteractionQueryRepository authenticationInteractionQueryRepository;

  public AuthenticationInteractionFindListService(
      AuthenticationInteractionQueryRepository authenticationInteractionQueryRepository) {
    this.authenticationInteractionQueryRepository = authenticationInteractionQueryRepository;
  }

  @Override
  public AuthenticationInteractionManagementResponse execute(
      AuthenticationInteractionManagementContextBuilder contextBuilder,
      Tenant tenant,
      User operator,
      OAuthToken oAuthToken,
      AuthenticationInteractionFindListRequest request,
      RequestAttributes requestAttributes) {

    long totalCount =
        authenticationInteractionQueryRepository.findTotalCount(tenant, request.queries());

    if (totalCount == 0) {
      Map<String, Object> response = new HashMap<>();
      response.put("list", List.of());
      response.put("total_count", 0);
      response.put("limit", request.queries().limit());
      response.put("offset", request.queries().offset());
      return new AuthenticationInteractionManagementResponse(
          AuthenticationInteractionManagementStatus.OK, response);
    }

    List<AuthenticationInteraction> interactions =
        authenticationInteractionQueryRepository.findList(tenant, request.queries());

    Map<String, Object> response = new HashMap<>();
    response.put("list", interactions.stream().map(AuthenticationInteraction::toMap).toList());
    response.put("total_count", totalCount);
    response.put("limit", request.queries().limit());
    response.put("offset", request.queries().offset());

    return new AuthenticationInteractionManagementResponse(
        AuthenticationInteractionManagementStatus.OK, response);
  }
}
