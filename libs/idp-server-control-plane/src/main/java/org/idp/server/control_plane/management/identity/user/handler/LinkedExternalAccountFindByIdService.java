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
import org.idp.server.control_plane.management.identity.user.io.LinkedExternalAccountByIdRequest;
import org.idp.server.control_plane.management.identity.user.io.UserManagementResponse;
import org.idp.server.control_plane.management.identity.user.io.UserManagementStatus;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.type.RequestAttributes;

/** Reads one of the tenant's linked external accounts by its identifier. */
public class LinkedExternalAccountFindByIdService
    implements UserManagementService<LinkedExternalAccountByIdRequest> {

  LinkedExternalAccountQueryRepository accountQueryRepository;

  public LinkedExternalAccountFindByIdService(
      LinkedExternalAccountQueryRepository accountQueryRepository) {
    this.accountQueryRepository = accountQueryRepository;
  }

  @Override
  public UserManagementResponse execute(
      UserManagementContextBuilder builder,
      Tenant tenant,
      User operator,
      OAuthToken oAuthToken,
      LinkedExternalAccountByIdRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    if (!request.isWellFormed()) {
      throw new ResourceNotFoundException("Linked external account not found.");
    }
    LinkedExternalAccount account = accountQueryRepository.find(tenant, request.identifier());
    if (!account.exists()) {
      throw new ResourceNotFoundException("Linked external account not found.");
    }
    return new UserManagementResponse(
        UserManagementStatus.OK, LinkedExternalAccountsFindAllService.toManagementMap(account));
  }
}
