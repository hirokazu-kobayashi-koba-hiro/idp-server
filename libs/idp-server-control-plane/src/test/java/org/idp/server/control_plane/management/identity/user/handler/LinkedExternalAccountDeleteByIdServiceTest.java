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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import org.idp.server.account_linking.AccountAlias;
import org.idp.server.account_linking.ExternalIdpProvider;
import org.idp.server.account_linking.LinkedExternalAccount;
import org.idp.server.account_linking.LinkedExternalAccountIdentifier;
import org.idp.server.account_linking.LinkedExternalAccountRevoker;
import org.idp.server.account_linking.repository.LinkedExternalAccountCommandRepository;
import org.idp.server.account_linking.repository.LinkedExternalAccountQueryRepository;
import org.idp.server.control_plane.management.identity.user.ManagementEventPublisher;
import org.idp.server.control_plane.management.identity.user.UserManagementContextBuilder;
import org.idp.server.control_plane.management.identity.user.io.LinkedExternalAccountByIdRequest;
import org.idp.server.control_plane.management.identity.user.io.UserManagementResponse;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.UserIdentifier;
import org.idp.server.core.openid.identity.repository.UserQueryRepository;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.type.RequestAttributes;
import org.junit.jupiter.api.Test;

class LinkedExternalAccountDeleteByIdServiceTest {

  static final String ID = "3f1c2b4a-5d6e-4f70-8a9b-0c1d2e3f4a5b";
  static final UserIdentifier GONE = new UserIdentifier("9a8b7c6d-5e4f-4a3b-8c2d-1e0f9a8b7c6d");

  UserQueryRepository users = mock(UserQueryRepository.class);
  LinkedExternalAccountQueryRepository accountQuery =
      mock(LinkedExternalAccountQueryRepository.class);
  LinkedExternalAccountCommandRepository accountCommand =
      mock(LinkedExternalAccountCommandRepository.class);
  LinkedExternalAccountRevoker revoker = mock(LinkedExternalAccountRevoker.class);
  ManagementEventPublisher events = mock(ManagementEventPublisher.class);
  Tenant tenant = mock(Tenant.class);

  LinkedExternalAccountDeleteByIdService service =
      new LinkedExternalAccountDeleteByIdService(
          users, accountQuery, accountCommand, revoker, events);

  /**
   * Deleting a user removes its links afterwards. If that failed, the link is left with working
   * tokens; removing it by id must still revoke and delete it.
   */
  @Test
  void removesALinkWhoseOwnerIsGone() {
    LinkedExternalAccount orphan =
        new LinkedExternalAccount.Builder()
            .identifier(new LinkedExternalAccountIdentifier(ID))
            .userIdentifier(GONE)
            .provider(new ExternalIdpProvider("example-idp"))
            .accountAlias(new AccountAlias("example-idp-abcd1234"))
            .build();
    when(accountQuery.lock(eq(tenant), any(LinkedExternalAccountIdentifier.class)))
        .thenReturn(orphan);
    when(users.findById(tenant, GONE)).thenReturn(new User());

    UserManagementResponse response =
        service.execute(
            mock(UserManagementContextBuilder.class),
            tenant,
            new User(),
            mock(OAuthToken.class),
            new LinkedExternalAccountByIdRequest(new LinkedExternalAccountIdentifier(ID)),
            new RequestAttributes(),
            false);

    assertEquals(204, response.statusCode());
    verify(revoker).revoke(tenant, orphan);
    verify(accountCommand).delete(tenant, orphan);
    verify(users, never()).get(any(), any());
  }
}
