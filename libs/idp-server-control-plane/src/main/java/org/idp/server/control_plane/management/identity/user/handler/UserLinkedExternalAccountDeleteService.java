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
import java.util.Map;
import org.idp.server.account_linking.LinkedExternalAccount;
import org.idp.server.account_linking.LinkedExternalAccountRevoker;
import org.idp.server.account_linking.repository.LinkedExternalAccountCommandRepository;
import org.idp.server.account_linking.repository.LinkedExternalAccountQueryRepository;
import org.idp.server.control_plane.management.exception.ResourceNotFoundException;
import org.idp.server.control_plane.management.identity.user.ManagementEventPublisher;
import org.idp.server.control_plane.management.identity.user.UserManagementContextBuilder;
import org.idp.server.control_plane.management.identity.user.io.UserLinkedExternalAccountRequest;
import org.idp.server.control_plane.management.identity.user.io.UserManagementResponse;
import org.idp.server.control_plane.management.identity.user.io.UserManagementStatus;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.repository.UserQueryRepository;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.security.event.DefaultSecurityEventType;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Removes one of a user's linked external accounts on an administrator's behalf.
 *
 * <p>Done the same way as the user's own unlink: the row is read under its lock, the stored tokens
 * are revoked at the external IdP on a best-effort basis, and the row goes either way. It is
 * recorded as a separate event, so that an audit can tell an administrator's removal from the
 * user's.
 */
public class UserLinkedExternalAccountDeleteService
    implements UserManagementService<UserLinkedExternalAccountRequest> {

  UserQueryRepository userQueryRepository;
  LinkedExternalAccountQueryRepository accountQueryRepository;
  LinkedExternalAccountCommandRepository accountCommandRepository;
  LinkedExternalAccountRevoker revoker;
  ManagementEventPublisher managementEventPublisher;

  public UserLinkedExternalAccountDeleteService(
      UserQueryRepository userQueryRepository,
      LinkedExternalAccountQueryRepository accountQueryRepository,
      LinkedExternalAccountCommandRepository accountCommandRepository,
      LinkedExternalAccountRevoker revoker,
      ManagementEventPublisher managementEventPublisher) {
    this.userQueryRepository = userQueryRepository;
    this.accountQueryRepository = accountQueryRepository;
    this.accountCommandRepository = accountCommandRepository;
    this.revoker = revoker;
    this.managementEventPublisher = managementEventPublisher;
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
    builder.withBefore(user);

    LinkedExternalAccount account =
        accountQueryRepository.lock(tenant, user.userIdentifier(), request.alias());
    if (!account.exists()) {
      throw new ResourceNotFoundException("Linked external account not found.");
    }

    if (dryRun) {
      Map<String, Object> response = new HashMap<>();
      response.put("message", "Linked external account would be removed");
      response.put("sub", user.sub());
      response.put("alias", account.accountAlias().value());
      response.put("dry_run", true);
      return new UserManagementResponse(UserManagementStatus.OK, response);
    }

    revoker.revoke(tenant, account);
    accountCommandRepository.delete(tenant, account);

    managementEventPublisher.publish(
        tenant,
        operator,
        user,
        oAuthToken,
        DefaultSecurityEventType.external_account_unlinked_by_admin.toEventType(),
        requestAttributes);

    return new UserManagementResponse(UserManagementStatus.NO_CONTENT, Map.of());
  }
}
