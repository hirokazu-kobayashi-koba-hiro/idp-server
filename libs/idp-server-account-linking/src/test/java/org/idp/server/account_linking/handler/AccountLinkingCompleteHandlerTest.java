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

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.account_linking.*;
import org.idp.server.account_linking.io.AccountLinkingCompleteRequest;
import org.idp.server.account_linking.io.AccountLinkingResult;
import org.idp.server.account_linking.repository.AccountLinkingSessionCommandRepository;
import org.idp.server.account_linking.repository.AccountLinkingSessionQueryRepository;
import org.idp.server.account_linking.repository.LinkedExternalAccountCommandRepository;
import org.idp.server.account_linking.repository.LinkedExternalAccountQueryRepository;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.UserIdentifier;
import org.idp.server.core.openid.oauth.type.oauth.RequestedClientId;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.federation.sso.oidc.OidcSsoConfiguration;
import org.idp.server.platform.crypto.SealedData;
import org.idp.server.platform.datasource.SqlDuplicateKeyException;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.junit.jupiter.api.Test;

class AccountLinkingCompleteHandlerTest {

  static final String TENANT_ID = "33333333-3333-3333-3333-333333333333";
  static final Tenant TENANT =
      new Tenant() {
        @Override
        public TenantIdentifier identifier() {
          return new TenantIdentifier(TENANT_ID);
        }

        @Override
        public String identifierValue() {
          return TENANT_ID;
        }
      };
  static final UserIdentifier OWNER = new UserIdentifier("11111111-1111-1111-1111-111111111111");
  static final ExternalIdpProvider PROVIDER = new ExternalIdpProvider("google");
  static final RequestedClientId CLIENT = new RequestedClientId("rp-client");
  static final AccountLinkingState STATE = new AccountLinkingState("state-value");

  static class Sessions
      implements AccountLinkingSessionQueryRepository, AccountLinkingSessionCommandRepository {
    Map<String, AccountLinkingSession> rows = new HashMap<>();
    int claims;

    @Override
    public AccountLinkingSession find(Tenant tenant, AccountLinkingState state) {
      return rows.getOrDefault(state.value(), new AccountLinkingSession());
    }

    @Override
    public List<AccountLinkingSession> findList(Tenant tenant, UserIdentifier userIdentifier) {
      return List.copyOf(rows.values());
    }

    @Override
    public void register(Tenant tenant, AccountLinkingSession session) {
      rows.put(session.state().value(), session);
    }

    @Override
    public boolean claim(
        Tenant tenant,
        AccountLinkingState state,
        AccountLinkingSessionStatus from,
        AccountLinkingSessionStatus to,
        LocalDateTime now) {
      claims++;
      return rows.containsKey(state.value()) && rows.get(state.value()).status() == from;
    }

    @Override
    public void update(Tenant tenant, AccountLinkingSession session) {}

    @Override
    public void delete(Tenant tenant, AccountLinkingState state) {
      rows.remove(state.value());
    }

    @Override
    public void deleteAll(Tenant tenant, UserIdentifier userIdentifier) {}
  }

  static class Accounts
      implements LinkedExternalAccountQueryRepository, LinkedExternalAccountCommandRepository {
    List<LinkedExternalAccount> rows = new ArrayList<>();
    boolean otherUserHolds;
    RuntimeException registerFailure;

    @Override
    public List<LinkedExternalAccount> findList(Tenant tenant, UserIdentifier userIdentifier) {
      return rows;
    }

    @Override
    public LinkedExternalAccount find(
        Tenant tenant, UserIdentifier userIdentifier, AccountAlias alias) {
      return rows.stream()
          .filter(it -> it.accountAlias().equals(alias))
          .findFirst()
          .orElse(new LinkedExternalAccount());
    }

    @Override
    public LinkedExternalAccount findByUserAndFederatedUser(
        Tenant tenant,
        UserIdentifier userIdentifier,
        ExternalIdpProvider provider,
        String federatedUserId) {
      return new LinkedExternalAccount();
    }

    @Override
    public boolean existsForOtherUser(
        Tenant tenant,
        UserIdentifier userIdentifier,
        ExternalIdpProvider provider,
        String federatedUserId) {
      return otherUserHolds;
    }

    @Override
    public LinkedExternalAccount lock(
        Tenant tenant, UserIdentifier userIdentifier, AccountAlias alias) {
      return find(tenant, userIdentifier, alias);
    }

    @Override
    public void register(Tenant tenant, LinkedExternalAccount account) {
      if (registerFailure != null) {
        throw registerFailure;
      }
      rows.add(account);
    }

    @Override
    public void update(Tenant tenant, LinkedExternalAccount account) {}

    @Override
    public void delete(Tenant tenant, LinkedExternalAccount account) {
      rows.remove(account);
    }

    @Override
    public void deleteAll(Tenant tenant, UserIdentifier userIdentifier) {}
  }

  /** Reject on duplicates, the default policy. */
  static class Resolver extends AccountLinkingConfigurationResolver {
    Resolver() {
      super(null);
    }

    @Override
    public AccountLinkingProviderConfiguration resolve(
        Tenant tenant, ExternalIdpProvider provider) {
      return new AccountLinkingProviderConfiguration(
          new OidcSsoConfiguration(), new AccountLinkingConfiguration());
    }
  }

  Sessions sessions = new Sessions();
  Accounts accounts = new Accounts();
  AccountLinkingCompleteHandler handler =
      new AccountLinkingCompleteHandler(sessions, sessions, accounts, accounts, new Resolver());

  void parkSession(String federatedUserId) {
    sessions.register(
        TENANT,
        new AccountLinkingSession.Builder()
            .state(STATE)
            .tenantIdentifier(new TenantIdentifier(TENANT_ID))
            .userIdentifier(OWNER)
            .requestedClientId(CLIENT)
            .provider(PROVIDER)
            .redirectUri("https://rp.example.com/linking/callback")
            .codeVerifier("code-verifier")
            .status(AccountLinkingSessionStatus.PARKED)
            .expiresAt(LocalDateTime.now().plusMinutes(15))
            .parkedCredentials(
                new ParkedCredentials.Builder()
                    .federatedUserId(federatedUserId)
                    .grantedScope("openid")
                    .encryptedAccessToken(new SealedData())
                    .encryptionKeyId("dek")
                    .build())
            .build());
  }

  AccountLinkingResult complete() {
    OAuthToken token =
        new OAuthToken() {
          @Override
          public RequestedClientId requestedClientId() {
            return CLIENT;
          }
        };
    return handler.handle(
        new AccountLinkingCompleteRequest(TENANT, STATE, new User().setSub(OWNER.value()), token));
  }

  @Test
  void aliasesAreNeverReusedAfterUnlinking() {
    parkSession(null);
    AccountLinkingResult first = complete();
    assertFalse(first.isError());
    AccountAlias firstAlias = accounts.rows.getFirst().accountAlias();

    // Unlink it, then link again: the new link must not take the old name, or an RP that kept it
    // would be handed another account's tokens.
    accounts.rows.clear();
    parkSession(null);
    assertFalse(complete().isError());
    AccountAlias second = accounts.rows.getFirst().accountAlias();

    assertNotEquals(firstAlias, second);
    assertTrue(second.value().matches("google-[a-z0-9]{8}"), second.value());
  }

  @Test
  void aRejectedLinkLeavesTheSessionUnspent() {
    accounts.otherUserHolds = true;
    parkSession("federated-sub");

    AccountLinkingResult result = complete();

    assertTrue(result.isError());
    assertEquals(409, result.statusCode());
    assertEquals(0, sessions.claims);
    assertTrue(sessions.find(TENANT, STATE).exists());
  }

  @Test
  void aFailureAfterClaimingIsRethrownSoTheTransactionRollsBack() {
    accounts.registerFailure = new SqlDuplicateKeyException("duplicate");
    parkSession(null);

    assertThrows(SqlDuplicateKeyException.class, this::complete);
  }
}
