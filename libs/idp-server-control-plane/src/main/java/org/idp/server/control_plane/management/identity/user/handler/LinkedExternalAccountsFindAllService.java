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

package org.idp.server.control_plane.management.identity.user.handler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.account_linking.LinkedExternalAccount;
import org.idp.server.account_linking.LinkedExternalAccountQueries;
import org.idp.server.account_linking.repository.LinkedExternalAccountQueryRepository;
import org.idp.server.control_plane.management.identity.user.UserManagementContextBuilder;
import org.idp.server.control_plane.management.identity.user.io.LinkedExternalAccountsFindAllRequest;
import org.idp.server.control_plane.management.identity.user.io.UserManagementResponse;
import org.idp.server.control_plane.management.identity.user.io.UserManagementStatus;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Lists the tenant's linked external accounts across users, for an administrator.
 *
 * <p>The per-user list answers a support question about one person; this one answers questions
 * about a provider, such as how many links it still holds before it is removed. The tokens never
 * leave.
 */
public class LinkedExternalAccountsFindAllService
    implements UserManagementService<LinkedExternalAccountsFindAllRequest> {

  LinkedExternalAccountQueryRepository accountQueryRepository;

  public LinkedExternalAccountsFindAllService(
      LinkedExternalAccountQueryRepository accountQueryRepository) {
    this.accountQueryRepository = accountQueryRepository;
  }

  @Override
  public UserManagementResponse execute(
      UserManagementContextBuilder builder,
      Tenant tenant,
      User operator,
      OAuthToken oAuthToken,
      LinkedExternalAccountsFindAllRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    LinkedExternalAccountQueries queries = request.queries();
    if (!queries.isUserIdValid()) {
      return new UserManagementResponse(
          UserManagementStatus.INVALID_REQUEST,
          Map.of("error", "invalid_request", "error_description", "user_id must be a UUID."));
    }

    long totalCount = accountQueryRepository.findTotalCount(tenant, queries);
    List<Map<String, Object>> list =
        totalCount == 0
            ? List.of()
            : accountQueryRepository.findList(tenant, queries).stream()
                .map(LinkedExternalAccountsFindAllService::toManagementMap)
                .toList();

    Map<String, Object> response = new HashMap<>();
    response.put("list", list);
    response.put("total_count", totalCount);
    response.put("limit", queries.limit());
    response.put("offset", queries.offset());
    return new UserManagementResponse(UserManagementStatus.OK, response);
  }

  /** The per-user view, plus whose link it is and its identifier for reading or removing it. */
  static Map<String, Object> toManagementMap(LinkedExternalAccount account) {
    Map<String, Object> map = UserLinkedExternalAccountsFindService.toManagementMap(account);
    map.put("id", account.identifier().value());
    map.put("user_id", account.userIdentifier().value());
    return map;
  }
}
