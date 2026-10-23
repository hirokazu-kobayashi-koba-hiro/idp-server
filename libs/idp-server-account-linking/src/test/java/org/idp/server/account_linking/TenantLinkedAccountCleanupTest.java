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

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.account_linking.repository.AccountLinkingSessionCommandRepository;
import org.idp.server.account_linking.repository.AccountLinkingSessionQueryRepository;
import org.idp.server.account_linking.repository.LinkedExternalAccountCommandRepository;
import org.idp.server.account_linking.repository.LinkedExternalAccountQueryRepository;
import org.idp.server.core.openid.identity.UserIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.junit.jupiter.api.Test;

class TenantLinkedAccountCleanupTest {

  static final Tenant TENANT = InMemoryDataKeys.TENANT;
  static final ExternalIdpProvider PROVIDER = new ExternalIdpProvider("example-idp");

  /** One tenant's links, held in memory. Pages come back from the first row. */
  static class Store
      implements LinkedExternalAccountQueryRepository, LinkedExternalAccountCommandRepository {
    Map<String, LinkedExternalAccount> accounts = new LinkedHashMap<>();
    int accountPagesRead;

    @Override
    public List<LinkedExternalAccount> findList(Tenant tenant, LinkedExternalAccountQueries q) {
      accountPagesRead++;
      return accounts.values().stream().limit(q.limit()).toList();
    }

    @Override
    public LinkedExternalAccount lock(Tenant tenant, LinkedExternalAccountIdentifier identifier) {
      return accounts.getOrDefault(identifier.value(), new LinkedExternalAccount());
    }

    @Override
    public void delete(Tenant tenant, LinkedExternalAccount account) {
      accounts.remove(account.identifier().value());
    }

    // Not used by the cleanup.
    @Override
    public List<LinkedExternalAccount> findList(Tenant tenant, UserIdentifier userIdentifier) {
      throw new UnsupportedOperationException();
    }

    @Override
    public LinkedExternalAccount find(
        Tenant tenant, UserIdentifier userIdentifier, AccountAlias alias) {
      throw new UnsupportedOperationException();
    }

    @Override
    public long findTotalCount(Tenant tenant, LinkedExternalAccountQueries queries) {
      throw new UnsupportedOperationException();
    }

    @Override
    public LinkedExternalAccount find(Tenant tenant, LinkedExternalAccountIdentifier identifier) {
      throw new UnsupportedOperationException();
    }

    @Override
    public LinkedExternalAccount findByUserAndFederatedUser(
        Tenant tenant,
        UserIdentifier userIdentifier,
        ExternalIdpProvider provider,
        String federatedUserId) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean existsForOtherUser(
        Tenant tenant,
        UserIdentifier userIdentifier,
        ExternalIdpProvider provider,
        String federatedUserId) {
      throw new UnsupportedOperationException();
    }

    @Override
    public LinkedExternalAccount lock(
        Tenant tenant, UserIdentifier userIdentifier, AccountAlias alias) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void register(Tenant tenant, LinkedExternalAccount account) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void update(Tenant tenant, LinkedExternalAccount account) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void deleteAll(Tenant tenant, UserIdentifier userIdentifier) {
      throw new UnsupportedOperationException();
    }
  }

  /** One tenant's linking sessions, held in memory. */
  static class Sessions
      implements AccountLinkingSessionQueryRepository, AccountLinkingSessionCommandRepository {
    Map<String, AccountLinkingSession> sessions = new LinkedHashMap<>();

    @Override
    public List<AccountLinkingSession> findParkedList(Tenant tenant, int limit) {
      return sessions.values().stream().filter(s -> s.status().isParked()).limit(limit).toList();
    }

    @Override
    public void delete(Tenant tenant, AccountLinkingState state) {
      sessions.remove(state.value());
    }

    @Override
    public AccountLinkingSession find(Tenant tenant, AccountLinkingState state) {
      throw new UnsupportedOperationException();
    }

    @Override
    public List<AccountLinkingSession> findList(Tenant tenant, UserIdentifier userIdentifier) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void register(Tenant tenant, AccountLinkingSession session) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean claim(
        Tenant tenant,
        AccountLinkingState state,
        AccountLinkingSessionStatus from,
        AccountLinkingSessionStatus to,
        LocalDateTime now) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void update(Tenant tenant, AccountLinkingSession session) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void deleteAll(Tenant tenant, UserIdentifier userIdentifier) {
      throw new UnsupportedOperationException();
    }
  }

  /** Records what it was asked to revoke; fails the ones named in {@code refusing}. */
  static class RecordingRevoker extends LinkedExternalAccountRevoker {
    List<String> revoked = new ArrayList<>();
    List<String> refusing = new ArrayList<>();

    RecordingRevoker() {
      super(null, null, null);
    }

    @Override
    public boolean revoke(Tenant tenant, LinkedExternalAccount account) {
      revoked.add(account.identifier().value());
      return !refusing.contains(account.identifier().value());
    }

    @Override
    public boolean revoke(Tenant tenant, AccountLinkingSession session) {
      revoked.add(session.state().value());
      return true;
    }
  }

  Store store = new Store();
  Sessions sessions = new Sessions();
  RecordingRevoker revoker = new RecordingRevoker();
  TenantLinkedAccountCleanup cleanup =
      new TenantLinkedAccountCleanup(store, store, sessions, sessions, revoker);

  void addAccount(String id) {
    store.accounts.put(
        id,
        new LinkedExternalAccount.Builder()
            .identifier(new LinkedExternalAccountIdentifier(id))
            .provider(PROVIDER)
            .build());
  }

  void addSession(String state, AccountLinkingSessionStatus status) {
    sessions.sessions.put(
        state,
        new AccountLinkingSession.Builder()
            .state(new AccountLinkingState(state))
            .provider(PROVIDER)
            .status(status)
            .build());
  }

  @Test
  void revokesAndRemovesEveryLinkAcrossPages() {
    int total = TenantLinkedAccountCleanup.PAGE_SIZE * 2 + 1;
    for (int i = 0; i < total; i++) {
      addAccount("account-" + i);
    }

    TenantLinkedAccountCleanupResult result = cleanup.cleanup(TENANT);

    assertTrue(store.accounts.isEmpty());
    assertEquals(total, revoker.revoked.size());
    assertEquals(total, result.accounts());
    assertEquals(total, result.revokedAccounts());
    assertEquals(4, store.accountPagesRead);
  }

  @Test
  void removesALinkWhoseRevocationFailed() {
    addAccount("refused");
    addAccount("accepted");
    revoker.refusing.add("refused");

    TenantLinkedAccountCleanupResult result = cleanup.cleanup(TENANT);

    assertTrue(store.accounts.isEmpty());
    assertEquals(2, result.accounts());
    assertEquals(1, result.revokedAccounts());
  }

  @Test
  void revokesOnlyParkedSessions() {
    addSession("parked", AccountLinkingSessionStatus.PARKED);
    addSession("pending", AccountLinkingSessionStatus.PENDING);
    addSession("consumed", AccountLinkingSessionStatus.CONSUMED);

    TenantLinkedAccountCleanupResult result = cleanup.cleanup(TENANT);

    assertEquals(List.of("parked"), revoker.revoked);
    assertEquals(List.of("pending", "consumed"), List.copyOf(sessions.sessions.keySet()));
    assertEquals(1, result.parkedSessions());
    assertEquals(1, result.revokedParkedSessions());
  }

  @Test
  void stopsWhenAPageRemovesNothing() {
    // Listed but gone by the time it is locked, as if the listing lagged behind.
    store =
        new Store() {
          @Override
          public LinkedExternalAccount lock(
              Tenant tenant, LinkedExternalAccountIdentifier identifier) {
            return new LinkedExternalAccount();
          }
        };
    store.accounts.put(
        "vanishing",
        new LinkedExternalAccount.Builder()
            .identifier(new LinkedExternalAccountIdentifier("vanishing"))
            .provider(PROVIDER)
            .build());
    cleanup = new TenantLinkedAccountCleanup(store, store, sessions, sessions, revoker);

    TenantLinkedAccountCleanupResult result = cleanup.cleanup(TENANT);

    assertEquals(0, result.accounts());
    assertEquals(1, store.accountPagesRead);
    assertTrue(revoker.revoked.isEmpty());
  }

  @Test
  void doesNothingForATenantWithoutLinks() {
    TenantLinkedAccountCleanupResult result = cleanup.cleanup(TENANT);

    assertEquals(new TenantLinkedAccountCleanupResult(0, 0, 0, 0), result);
    assertTrue(revoker.revoked.isEmpty());
  }
}
