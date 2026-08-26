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
import org.idp.server.account_linking.repository.LinkedExternalAccountQueryRepository;
import org.idp.server.account_linking.verifier.DuplicateLinkVerifier;
import org.idp.server.core.openid.identity.User;
import org.idp.server.platform.date.SystemDateTime;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.security.event.DefaultSecurityEventType;

/** Claims the parked tokens on behalf of the Bearer-authenticated user and writes the link. */
public class AccountLinkingCompleteHandler {

  AccountLinkingSessionQueryRepository sessionQueryRepository;
  AccountLinkingSessionCommandRepository sessionCommandRepository;
  LinkedExternalAccountQueryRepository accountQueryRepository;
  LinkedExternalAccountCommandRepository accountCommandRepository;
  AccountLinkingConfigurationResolver configurationResolver;
  DuplicateLinkVerifier duplicateLinkVerifier;
  AccountLinkingErrorHandler errorHandler;

  public AccountLinkingCompleteHandler(
      AccountLinkingSessionQueryRepository sessionQueryRepository,
      AccountLinkingSessionCommandRepository sessionCommandRepository,
      LinkedExternalAccountQueryRepository accountQueryRepository,
      LinkedExternalAccountCommandRepository accountCommandRepository,
      AccountLinkingConfigurationResolver configurationResolver) {
    this.sessionQueryRepository = sessionQueryRepository;
    this.sessionCommandRepository = sessionCommandRepository;
    this.accountQueryRepository = accountQueryRepository;
    this.accountCommandRepository = accountCommandRepository;
    this.configurationResolver = configurationResolver;
    this.duplicateLinkVerifier = new DuplicateLinkVerifier(accountQueryRepository);
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

      AccountLinkingSession consumed = session.consume(user.userIdentifier());

      if (!sessionCommandRepository.claim(
          tenant,
          state,
          AccountLinkingSessionStatus.PARKED,
          AccountLinkingSessionStatus.CONSUMED,
          now)) {
        throw new AccountLinkingSessionStateException(
            "Account linking session is not awaiting completion.");
      }

      LinkedExternalAccount account = store(tenant, consumed, now);
      sessionCommandRepository.delete(tenant, state);

      return AccountLinkingResult.success(
          AccountLinkingStatus.CREATED,
          account.toMap(),
          DefaultSecurityEventType.external_account_linked,
          user);

    } catch (Exception exception) {
      return errorHandler
          .handle(exception)
          .withContext(request.oAuthToken().requestedClientId(), user);
    }
  }

  /** Writes the link, either as a new row or as a re-link of one this user already holds. */
  private LinkedExternalAccount store(
      Tenant tenant, AccountLinkingSession session, LocalDateTime now) {
    ParkedCredentials credentials = session.parkedCredentials();

    // Without an identifier from the provider there is nothing to compare, so neither recognising
    // a re-link nor the duplicate policy applies. Every such link is a new delegation, which is
    // what an OAuth 2.0 grant is.
    if (credentials.hasFederatedUserId()) {
      LinkedExternalAccount own =
          accountQueryRepository.findByUserAndFederatedUser(
              tenant, session.userIdentifier(), session.provider(), credentials.federatedUserId());

      if (own.exists()) {
        LinkedExternalAccount relinked = own.relinkedWith(credentials, now);
        accountCommandRepository.update(tenant, relinked);
        return relinked;
      }

      duplicateLinkVerifier.verify(
          tenant,
          session,
          credentials.federatedUserId(),
          configurationResolver.linking(tenant, session.provider()).duplicateLinkPolicy());
    }

    LinkedExternalAccount account =
        LinkedExternalAccount.fromConsumedSession(session, nextAlias(tenant, session), now);
    accountCommandRepository.register(tenant, account);
    return account;
  }

  private AccountAlias nextAlias(Tenant tenant, AccountLinkingSession session) {
    if (session.hasAccountAlias()) {
      return session.accountAlias();
    }
    int linked =
        accountQueryRepository.countByProvider(
            tenant, session.userIdentifier(), session.provider());
    return AccountAlias.of(session.provider(), linked + 1);
  }

  private AccountLinkingSession getSession(Tenant tenant, AccountLinkingState state) {
    AccountLinkingSession session = sessionQueryRepository.find(tenant, state);
    if (!session.exists()) {
      throw new AccountLinkingNotFoundException("Account linking session not found.");
    }
    return session;
  }
}
