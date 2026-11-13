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

import org.idp.server.account_linking.LinkedExternalAccount;
import org.idp.server.account_linking.repository.LinkedExternalAccountQueryRepository;
import org.idp.server.control_plane.management.exception.ResourceNotFoundException;
import org.idp.server.control_plane.management.identity.user.UserManagementContextBuilder;
import org.idp.server.control_plane.management.identity.user.io.UserLinkedExternalAccountRequest;
import org.idp.server.control_plane.management.identity.user.io.UserManagementResponse;
import org.idp.server.control_plane.management.identity.user.io.UserManagementStatus;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.repository.UserQueryRepository;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.type.RequestAttributes;

/** Reads one of a user's linked external accounts for an administrator. The tokens never leave. */
public class UserLinkedExternalAccountFindService
    implements UserManagementService<UserLinkedExternalAccountRequest> {

  UserQueryRepository userQueryRepository;
  LinkedExternalAccountQueryRepository accountQueryRepository;

  public UserLinkedExternalAccountFindService(
      UserQueryRepository userQueryRepository,
      LinkedExternalAccountQueryRepository accountQueryRepository) {
    this.userQueryRepository = userQueryRepository;
    this.accountQueryRepository = accountQueryRepository;
  }

  @Override
  public UserManagementResponse execute(
      UserManagementContextBuilder builder,
      Tenant tenant,
      User operator,
      OAuthToken oAuthToken,
      UserLinkedExternalAccountRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    User user = userQueryRepository.get(tenant, request.userIdentifier());

    LinkedExternalAccount account =
        accountQueryRepository.find(tenant, user.userIdentifier(), request.alias());
    if (!account.exists()) {
      throw new ResourceNotFoundException("Linked external account not found.");
    }

    return new UserManagementResponse(
        UserManagementStatus.OK, UserLinkedExternalAccountsFindService.toManagementMap(account));
  }
}
