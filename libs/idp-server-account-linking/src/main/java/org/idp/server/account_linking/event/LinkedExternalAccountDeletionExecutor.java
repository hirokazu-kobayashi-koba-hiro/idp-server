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
package org.idp.server.account_linking.event;

import java.util.List;
import java.util.Map;
import org.idp.server.account_linking.AccountLinkingSession;
import org.idp.server.account_linking.LinkedExternalAccount;
import org.idp.server.account_linking.LinkedExternalAccountRevoker;
import org.idp.server.account_linking.repository.AccountLinkingSessionCommandRepository;
import org.idp.server.account_linking.repository.AccountLinkingSessionQueryRepository;
import org.idp.server.account_linking.repository.LinkedExternalAccountCommandRepository;
import org.idp.server.account_linking.repository.LinkedExternalAccountQueryRepository;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.event.UserLifecycleEvent;
import org.idp.server.core.openid.identity.event.UserLifecycleEventExecutor;
import org.idp.server.core.openid.identity.event.UserLifecycleEventResult;
import org.idp.server.core.openid.identity.event.UserLifecycleType;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

/**
 * Removes a deleted user's linked external accounts, revoking their tokens at the external IdP.
 *
 * <p>{@code linked_external_accounts} has no foreign key to the user on purpose, so nothing else
 * removes these rows. Left behind, they would keep working external tokens for a user who no longer
 * exists. Revocation is best effort, as on unlink: the rows go either way.
 *
 * <p>Each account is re-read under its row lock before its tokens are revoked, for the same reason
 * as on unlink: a refresh running at the same moment may have rotated them.
 *
 * <p>Linking sessions the user started go too. A parked one holds tokens from a callback nobody
 * completed, and with the user gone nobody can: completing takes that user's own access token.
 */
public class LinkedExternalAccountDeletionExecutor implements UserLifecycleEventExecutor {

  LinkedExternalAccountQueryRepository accountQueryRepository;
  LinkedExternalAccountCommandRepository accountCommandRepository;
  AccountLinkingSessionQueryRepository sessionQueryRepository;
  AccountLinkingSessionCommandRepository sessionCommandRepository;
  LinkedExternalAccountRevoker revoker;
  LoggerWrapper log = LoggerWrapper.getLogger(LinkedExternalAccountDeletionExecutor.class);

  public LinkedExternalAccountDeletionExecutor(
      LinkedExternalAccountQueryRepository accountQueryRepository,
      LinkedExternalAccountCommandRepository accountCommandRepository,
      AccountLinkingSessionQueryRepository sessionQueryRepository,
      AccountLinkingSessionCommandRepository sessionCommandRepository,
      LinkedExternalAccountRevoker revoker) {
    this.accountQueryRepository = accountQueryRepository;
    this.accountCommandRepository = accountCommandRepository;
    this.sessionQueryRepository = sessionQueryRepository;
    this.sessionCommandRepository = sessionCommandRepository;
    this.revoker = revoker;
  }

  @Override
  public UserLifecycleType lifecycleType() {
    return UserLifecycleType.DELETE;
  }

  @Override
  public String name() {
    return "linked-external-account-deletion";
  }

  @Override
  public boolean shouldExecute(UserLifecycleEvent userLifecycleEvent) {
    return userLifecycleEvent.lifecycleType() == UserLifecycleType.DELETE;
  }

  @Override
  public UserLifecycleEventResult execute(UserLifecycleEvent userLifecycleEvent) {
    try {
      Tenant tenant = userLifecycleEvent.tenant();
      User user = userLifecycleEvent.user();

      List<LinkedExternalAccount> accounts =
          accountQueryRepository.findList(tenant, user.userIdentifier());
      int revokedCount = 0;
      for (LinkedExternalAccount listed : accounts) {
        LinkedExternalAccount account =
            accountQueryRepository.lock(tenant, user.userIdentifier(), listed.accountAlias());
        if (account.exists() && revoker.revoke(tenant, account)) {
          revokedCount++;
        }
      }
      accountCommandRepository.deleteAll(tenant, user.userIdentifier());

      List<AccountLinkingSession> sessions =
          sessionQueryRepository.findList(tenant, user.userIdentifier());
      for (AccountLinkingSession session : sessions) {
        revoker.revoke(tenant, session);
      }
      sessionCommandRepository.deleteAll(tenant, user.userIdentifier());

      if (!accounts.isEmpty() || !sessions.isEmpty()) {
        log.info(
            "Linked external accounts deleted for deleted user: user={}, count={}, revoked={},"
                + " sessions={}",
            user.sub(),
            accounts.size(),
            revokedCount,
            sessions.size());
      }
      return UserLifecycleEventResult.success(
          name(),
          Map.of(
              "deleted_count",
              accounts.size(),
              "revoked_count",
              revokedCount,
              "deleted_session_count",
              sessions.size()));
    } catch (Exception e) {

      log.error("UserLifecycleEventExecutor error: {}", e.getMessage(), e);
      return UserLifecycleEventResult.failure(
          name(), Map.of("error", String.valueOf(e.getMessage())));
    }
  }
}
