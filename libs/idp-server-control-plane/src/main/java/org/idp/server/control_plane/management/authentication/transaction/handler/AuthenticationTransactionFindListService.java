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

package org.idp.server.control_plane.management.authentication.transaction.handler;

import java.util.List;
import java.util.Map;
import org.idp.server.control_plane.management.authentication.transaction.AuthenticationTransactionManagementContextBuilder;
import org.idp.server.control_plane.management.authentication.transaction.io.AuthenticationTransactionFindListRequest;
import org.idp.server.control_plane.management.authentication.transaction.io.AuthenticationTransactionManagementResponse;
import org.idp.server.control_plane.management.authentication.transaction.io.AuthenticationTransactionManagementStatus;
import org.idp.server.core.openid.authentication.AuthenticationTransaction;
import org.idp.server.core.openid.authentication.repository.AuthenticationTransactionQueryRepository;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Service for finding authentication transaction list.
 *
 * <p>Handles authentication transaction list retrieval logic. This is a read-only operation that
 * supports pagination and filtering.
 *
 * <h2>Responsibilities</h2>
 *
 * <ul>
 *   <li>Query execution with pagination
 *   <li>Total count retrieval
 *   <li>Response formatting
 * </ul>
 */
public class AuthenticationTransactionFindListService
    implements AuthenticationTransactionManagementService<
        AuthenticationTransactionFindListRequest> {

  private final AuthenticationTransactionQueryRepository authenticationTransactionQueryRepository;

  public AuthenticationTransactionFindListService(
      AuthenticationTransactionQueryRepository authenticationTransactionQueryRepository) {
    this.authenticationTransactionQueryRepository = authenticationTransactionQueryRepository;
  }

  @Override
  public AuthenticationTransactionManagementResponse execute(
      AuthenticationTransactionManagementContextBuilder contextBuilder,
      Tenant tenant,
      User operator,
      OAuthToken oAuthToken,
      AuthenticationTransactionFindListRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    long totalCount =
        authenticationTransactionQueryRepository.findTotalCount(tenant, request.queries());
    if (totalCount == 0) {
      Map<String, Object> response =
          Map.of(
              "list", List.of(),
              "total_count", 0,
              "limit", request.queries().limit(),
              "offset", request.queries().offset());
      return new AuthenticationTransactionManagementResponse(
          AuthenticationTransactionManagementStatus.OK, response);
    }

    List<AuthenticationTransaction> authenticationTransactions =
        authenticationTransactionQueryRepository.findList(tenant, request.queries());

    Map<String, Object> response =
        Map.of(
            "list",
                authenticationTransactions.stream()
                    .map(AuthenticationTransaction::toRequestMap)
                    .toList(),
            "total_count", totalCount,
            "limit", request.queries().limit(),
            "offset", request.queries().offset());

    return new AuthenticationTransactionManagementResponse(
        AuthenticationTransactionManagementStatus.OK, response);
  }
}
