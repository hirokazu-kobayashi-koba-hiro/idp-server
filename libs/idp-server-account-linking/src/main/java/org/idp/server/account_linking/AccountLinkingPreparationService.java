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

package org.idp.server.account_linking;

import java.time.LocalDateTime;
import org.idp.server.account_linking.repository.LinkedExternalAccountQueryRepository;
import org.idp.server.account_linking.verifier.DuplicateLinkVerifier;
import org.idp.server.account_linking.verifier.SameExternalAccountVerifier;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

/**
 * Decides what a completion writes: a re-link of one the user already holds, or a new link.
 *
 * <p>Everything that can refuse the link is decided here, before the session is spent, so a refused
 * completion leaves the parked tokens where they were. A link to be renewed is read under its row
 * lock, as token retrieval refreshes under the same lock: without it, a refresh running alongside
 * would write its tokens over the ones this link brings.
 */
public class AccountLinkingPreparationService {

  LinkedExternalAccountQueryRepository accountQueryRepository;
  DuplicateLinkVerifier duplicateLinkVerifier;
  SameExternalAccountVerifier sameExternalAccountVerifier;

  public AccountLinkingPreparationService(
      LinkedExternalAccountQueryRepository accountQueryRepository) {
    this.accountQueryRepository = accountQueryRepository;
    this.duplicateLinkVerifier = new DuplicateLinkVerifier(accountQueryRepository);
    this.sameExternalAccountVerifier = new SameExternalAccountVerifier();
  }

  public PreparedLink prepare(
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
   * <p>Two completions that both find no link race to insert; the database's one-account index lets
   * only one of them through.
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

    LinkedExternalAccount renewed =
        ProviderLinks.of(
                accountQueryRepository.findList(tenant, session.userIdentifier()),
                session.provider())
            .theOneToRenew(credentials);
    if (renewed.exists()) {
      LinkedExternalAccount current =
          accountQueryRepository.lock(tenant, session.userIdentifier(), renewed.accountAlias());
      sameExternalAccountVerifier.verify(current, credentials);
      if (current.isFirstNamedBy(credentials)) {
        duplicateLinkVerifier.verify(
            tenant, session, credentials.federatedUserId(), configuration.duplicateLinkPolicy());
      }
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
   * One of several links to the provider: the same external account is renewed, another is added.
   *
   * <p>Without an identifier from the provider there is nothing to compare, so neither recognising
   * a re-link nor the duplicate policy applies, and every such link is added.
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
}
