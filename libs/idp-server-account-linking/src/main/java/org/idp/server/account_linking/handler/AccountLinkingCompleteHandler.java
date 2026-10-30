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

package org.idp.server.account_linking.handler;

import java.time.LocalDateTime;
import org.idp.server.account_linking.*;
import org.idp.server.account_linking.exception.AccountLinkingNotFoundException;
import org.idp.server.account_linking.exception.AccountLinkingSessionStateException;
import org.idp.server.account_linking.io.AccountLinkingCompleteRequest;
import org.idp.server.account_linking.io.AccountLinkingResult;
import org.idp.server.account_linking.io.AccountLinkingStatus;
import org.idp.server.account_linking.repository.AccountLinkingSessionCommandRepository;
import org.idp.server.account_linking.repository.AccountLinkingSessionQueryRepository;
import org.idp.server.account_linking.repository.LinkedExternalAccountCommandRepository;
import org.idp.server.core.openid.identity.User;
import org.idp.server.platform.date.SystemDateTime;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.security.event.DefaultSecurityEventType;

/**
 * Claims the parked tokens on behalf of the Bearer-authenticated user and writes the link.
 *
 * <p>What is written — a new link, or which one is renewed — is {@link
 * AccountLinkingPreparationService}'s to decide.
 */
public class AccountLinkingCompleteHandler {

  AccountLinkingSessionQueryRepository sessionQueryRepository;
  AccountLinkingSessionCommandRepository sessionCommandRepository;
  LinkedExternalAccountCommandRepository accountCommandRepository;
  AccountLinkingConfigurationResolver configurationResolver;
  AccountLinkingPreparationService preparationService;
  LinkedExternalAccountRevoker revoker;
  AccountLinkingErrorHandler errorHandler;

  public AccountLinkingCompleteHandler(
      AccountLinkingSessionQueryRepository sessionQueryRepository,
      AccountLinkingSessionCommandRepository sessionCommandRepository,
      LinkedExternalAccountCommandRepository accountCommandRepository,
      AccountLinkingConfigurationResolver configurationResolver,
      AccountLinkingPreparationService preparationService,
      LinkedExternalAccountRevoker revoker) {
    this.sessionQueryRepository = sessionQueryRepository;
    this.sessionCommandRepository = sessionCommandRepository;
    this.accountCommandRepository = accountCommandRepository;
    this.configurationResolver = configurationResolver;
    this.preparationService = preparationService;
    this.revoker = revoker;
    this.errorHandler = new AccountLinkingErrorHandler();
  }

  public AccountLinkingResult handle(AccountLinkingCompleteRequest request) {

    Tenant tenant = request.tenant();
    AccountLinkingState state = request.state();
    User user = request.user();
    LocalDateTime now = SystemDateTime.now();

    try {
      AccountLinkingSession session = getSession(tenant, state);
      session.verifyNotExpired(now);
      session.verifyClient(request.oAuthToken().requestedClientId());

      AccountLinkingSession consumed = session.consume(user.userIdentifier());
      AccountLinkingProviderConfiguration configuration =
          configurationResolver.resolve(tenant, consumed.provider());

      // Everything that can reject the link is decided before the session is spent, so a rejected
      // completion leaves the parked tokens where they were.
      PreparedLink link = preparationService.prepare(tenant, consumed, configuration, now);

      if (!sessionCommandRepository.claim(
          tenant,
          state,
          AccountLinkingSessionStatus.PARKED,
          AccountLinkingSessionStatus.CONSUMED,
          now)) {
        throw new AccountLinkingSessionStateException(
            "Account linking session is not awaiting completion.");
      }

      if (link.isRelink()) {
        accountCommandRepository.update(tenant, link.account());
      } else {
        accountCommandRepository.register(tenant, link.account());
      }
      sessionCommandRepository.delete(tenant, state);
      // After the write: were the old tokens revoked first and the write then failed, the row would
      // be left holding revoked tokens. Revocation is best effort and never fails the link.
      if (link.revokesReplaced(configuration)) {
        revoker.revokeReplaced(tenant, link.replaced(), link.account());
      }

      return AccountLinkingResult.success(
          AccountLinkingStatus.CREATED,
          link.account().toMap(),
          DefaultSecurityEventType.external_account_linked,
          user);

    } catch (RuntimeException exception) {
      // A failure after the session was claimed must not be answered as a normal result: the
      // transaction would commit the spent session without the link. It is rethrown so the
      // transaction rolls back and the caller can complete again.
      if (!errorHandler.isRejection(exception)) {
        throw exception;
      }
      return errorHandler
          .handle(exception)
          .withContext(request.oAuthToken().requestedClientId(), user);
    }
  }

  private AccountLinkingSession getSession(Tenant tenant, AccountLinkingState state) {
    AccountLinkingSession session = sessionQueryRepository.find(tenant, state);
    if (!session.exists()) {
      throw new AccountLinkingNotFoundException("Account linking session not found.");
    }
    return session;
  }
}
