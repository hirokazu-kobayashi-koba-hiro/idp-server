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
import java.util.List;
import org.idp.server.account_linking.*;
import org.idp.server.account_linking.exception.AccountLinkingAnotherAccountLinkedException;
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
import org.idp.server.account_linking.verifier.SameExternalAccountVerifier;
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
  SameExternalAccountVerifier sameExternalAccountVerifier;
  LinkedExternalAccountRevoker revoker;
  AccountLinkingErrorHandler errorHandler;

  public AccountLinkingCompleteHandler(
      AccountLinkingSessionQueryRepository sessionQueryRepository,
      AccountLinkingSessionCommandRepository sessionCommandRepository,
      LinkedExternalAccountQueryRepository accountQueryRepository,
      LinkedExternalAccountCommandRepository accountCommandRepository,
      AccountLinkingConfigurationResolver configurationResolver,
      LinkedExternalAccountRevoker revoker) {
    this.sessionQueryRepository = sessionQueryRepository;
    this.sessionCommandRepository = sessionCommandRepository;
    this.accountQueryRepository = accountQueryRepository;
    this.accountCommandRepository = accountCommandRepository;
    this.configurationResolver = configurationResolver;
    this.duplicateLinkVerifier = new DuplicateLinkVerifier(accountQueryRepository);
    this.sameExternalAccountVerifier = new SameExternalAccountVerifier();
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
      PreparedLink link = prepare(tenant, consumed, configuration, now);

      if (!sessionCommandRepository.claim(
          tenant,
          state,
          AccountLinkingSessionStatus.PARKED,
          AccountLinkingSessionStatus.CONSUMED,
          now)) {
        throw new AccountLinkingSessionStateException(
            "Account linking session is not awaiting completion.");
      }

      if (link.relink()) {
        accountCommandRepository.update(tenant, link.account());
      } else {
        accountCommandRepository.register(tenant, link.account());
      }
      sessionCommandRepository.delete(tenant, state);
      revokeReplaced(tenant, configuration, link);

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

  /** The link to write: a re-link of one this user already holds, or a new one. */
  private PreparedLink prepare(
      Tenant tenant,
      AccountLinkingSession session,
      AccountLinkingProviderConfiguration configuration,
      LocalDateTime now) {
    if (configuration.allowsMultipleAccounts()) {
      return prepareOneOfSeveral(tenant, session, configuration, now);
    }
    return prepareTheOne(tenant, session, configuration, now);
  }

  /**
   * The user's one link to the provider: linking again renews it, with the same external account.
   *
   * <p>The row is read under its lock, as token retrieval refreshes under the same lock. Without
   * it, a refresh running alongside would write its tokens over the ones this link brings. Two
   * completions that both find no link race to insert; the database's one-account index lets only
   * one of them through.
   *
   * <p>The alias stays random. Were it the provider's name, it would come back after unlinking and
   * name whichever account was linked next, and an RP holding it would be handed that account's
   * tokens without noticing; a new alias answers its old one with 404 instead.
   */
  private PreparedLink prepareTheOne(
      Tenant tenant,
      AccountLinkingSession session,
      AccountLinkingProviderConfiguration configuration,
      LocalDateTime now) {
    ParkedCredentials credentials = session.parkedCredentials();

    LinkedExternalAccount current = lockTheOne(tenant, session);
    if (current.exists()) {
      sameExternalAccountVerifier.verify(current, credentials);
      return PreparedLink.relink(current, current.relinkedWith(credentials, now));
    }

    if (credentials.hasFederatedUserId()) {
      duplicateLinkVerifier.verify(
          tenant, session, credentials.federatedUserId(), configuration.duplicateLinkPolicy());
    }
    AccountAlias alias = AccountAlias.generate(session.provider());
    return PreparedLink.created(
        LinkedExternalAccount.fromConsumedSession(session, alias, true, now));
  }

  /**
   * The user's link to the provider, locked; none if there is none.
   *
   * <p>More than one is possible only for links made while the provider still allowed several. The
   * one for the same external account is renewed; failing that, which to renew cannot be told, so
   * the user is asked to unlink first.
   */
  private LinkedExternalAccount lockTheOne(Tenant tenant, AccountLinkingSession session) {
    ParkedCredentials credentials = session.parkedCredentials();
    List<LinkedExternalAccount> linked =
        accountQueryRepository.findList(tenant, session.userIdentifier()).stream()
            .filter(account -> account.provider().equals(session.provider()))
            .toList();
    if (linked.isEmpty()) {
      return new LinkedExternalAccount();
    }

    LinkedExternalAccount chosen =
        linked.stream()
            .filter(
                account ->
                    credentials.hasFederatedUserId()
                        && credentials.federatedUserId().equals(account.federatedUserId()))
            .findFirst()
            .orElse(linked.size() == 1 ? linked.getFirst() : null);
    if (chosen == null) {
      throw new AccountLinkingAnotherAccountLinkedException(
          "Several accounts of this provider are linked. Unlink them before linking again.");
    }
    return accountQueryRepository.lock(tenant, session.userIdentifier(), chosen.accountAlias());
  }

  /**
   * One of several links to the provider: the same external account is renewed, another is added.
   *
   * <p>Without an identifier from the provider there is nothing to compare, so neither recognising
   * a re-link nor the duplicate policy applies, and every such link is added.
   *
   * <p>A link renewed here is locked for the same reason as in {@link #prepareTheOne}.
   */
  private PreparedLink prepareOneOfSeveral(
      Tenant tenant,
      AccountLinkingSession session,
      AccountLinkingProviderConfiguration configuration,
      LocalDateTime now) {
    ParkedCredentials credentials = session.parkedCredentials();

    if (credentials.hasFederatedUserId()) {
      LinkedExternalAccount own =
          accountQueryRepository.findByUserAndFederatedUser(
              tenant, session.userIdentifier(), session.provider(), credentials.federatedUserId());

      if (own.exists()) {
        LinkedExternalAccount locked =
            accountQueryRepository.lock(tenant, session.userIdentifier(), own.accountAlias());
        return PreparedLink.relink(locked, locked.relinkedWith(credentials, now));
      }

      duplicateLinkVerifier.verify(
          tenant, session, credentials.federatedUserId(), configuration.duplicateLinkPolicy());
    }

    AccountAlias alias = AccountAlias.generate(session.provider());
    return PreparedLink.created(
        LinkedExternalAccount.fromConsumedSession(session, alias, false, now));
  }

  /**
   * Revokes the tokens a re-link replaced, when the provider is set to.
   *
   * <p>It runs after the link is written: were the old tokens revoked first and the write then
   * failed, the row would be left holding revoked tokens. Revocation is best effort and never fails
   * the link.
   */
  private void revokeReplaced(
      Tenant tenant, AccountLinkingProviderConfiguration configuration, PreparedLink link) {
    if (link.relink() && configuration.revokesOnRelink()) {
      revoker.revokeReplaced(tenant, link.replaced(), link.account());
    }
  }

  /** The link to write, and for a re-link the row as it was before, holding the old tokens. */
  record PreparedLink(LinkedExternalAccount account, LinkedExternalAccount replaced) {

    static PreparedLink created(LinkedExternalAccount account) {
      return new PreparedLink(account, null);
    }

    static PreparedLink relink(LinkedExternalAccount replaced, LinkedExternalAccount account) {
      return new PreparedLink(account, replaced);
    }

    boolean relink() {
      return replaced != null;
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
