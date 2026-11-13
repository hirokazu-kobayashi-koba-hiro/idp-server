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

import java.util.ArrayList;
import java.util.List;
import org.idp.server.account_linking.gateway.ExternalIdpTokenGateway;
import org.idp.server.core.openid.federation.FederationConfigurationNotFoundException;
import org.idp.server.core.openid.identity.UserIdentifier;
import org.idp.server.federation.sso.oidc.OidcSsoConfiguration;
import org.idp.server.platform.crypto.SealedData;
import org.idp.server.platform.crypto.datakey.TenantDataCipher;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.junit.jupiter.api.Test;

class LinkedExternalAccountRevokerTest {

  static final String REVOCATION_ENDPOINT = "https://idp.example.com/revoke";
  static final ExternalIdpProvider PROVIDER = new ExternalIdpProvider("example-idp");

  static final Tenant TENANT = InMemoryDataKeys.TENANT;
  static final UserIdentifier USER = new UserIdentifier("11111111-1111-1111-1111-111111111111");

  TenantDataCipher aesCipher = AccountLinkingTokenCipher.from(InMemoryDataKeys.service());

  /** Records each revocation instead of calling the external IdP. */
  static class RecordingGateway extends ExternalIdpTokenGateway {
    List<String> revoked = new ArrayList<>();
    boolean accept = true;
    RuntimeException failure;

    RecordingGateway() {
      super(null);
    }

    @Override
    public boolean revoke(
        OidcSsoConfiguration configuration,
        String revocationEndpoint,
        String token,
        String tokenTypeHint) {
      if (failure != null) {
        throw failure;
      }
      revoked.add(tokenTypeHint + ":" + token);
      return accept;
    }
  }

  static class FixedResolver extends AccountLinkingConfigurationResolver {
    AccountLinkingProviderConfiguration configuration;
    boolean resolvedIncludingDisabled;

    FixedResolver(AccountLinkingProviderConfiguration configuration) {
      super(null);
      this.configuration = configuration;
    }

    @Override
    public AccountLinkingProviderConfiguration resolveIncludingDisabled(
        Tenant tenant, ExternalIdpProvider provider) {
      resolvedIncludingDisabled = true;
      if (configuration == null) {
        throw new FederationConfigurationNotFoundException("not found");
      }
      return configuration;
    }
  }

  static AccountLinkingProviderConfiguration configuration(String revocationEndpoint) {
    AccountLinkingConfiguration linking = new AccountLinkingConfiguration();
    linking.revocationEndpoint = revocationEndpoint;
    return new AccountLinkingProviderConfiguration(new OidcSsoConfiguration(), linking);
  }

  TenantDataCipher.Sealer sealer() {
    return aesCipher.sealer(TENANT);
  }

  LinkedExternalAccount account(String accessToken, String refreshToken) {
    return new LinkedExternalAccount.Builder()
        .userIdentifier(USER)
        .provider(PROVIDER)
        .encryptedAccessToken(
            sealer().seal(accessToken, AccountLinkingTokenCipher.accessToken(USER, PROVIDER)))
        .encryptedRefreshToken(
            refreshToken == null
                ? null
                : sealer()
                    .seal(refreshToken, AccountLinkingTokenCipher.refreshToken(USER, PROVIDER)))
        .encryptionKeyId(sealer().keyId())
        .build();
  }

  AccountLinkingSession session(AccountLinkingSessionStatus status) {
    return new AccountLinkingSession.Builder()
        .userIdentifier(USER)
        .provider(PROVIDER)
        .status(status)
        .parkedCredentials(
            new ParkedCredentials.Builder()
                .encryptedAccessToken(
                    sealer()
                        .seal("parked-at", AccountLinkingTokenCipher.accessToken(USER, PROVIDER)))
                .encryptedRefreshToken(
                    sealer()
                        .seal("parked-rt", AccountLinkingTokenCipher.refreshToken(USER, PROVIDER)))
                .encryptionKeyId(sealer().keyId())
                .build())
        .build();
  }

  @Test
  void revokesRefreshTokenFirstThenAccessToken() {
    RecordingGateway gateway = new RecordingGateway();
    FixedResolver resolver = new FixedResolver(configuration(REVOCATION_ENDPOINT));
    LinkedExternalAccountRevoker revoker =
        new LinkedExternalAccountRevoker(resolver, gateway, aesCipher);

    assertTrue(revoker.revoke(TENANT, account("at", "rt")));
    assertEquals(List.of("refresh_token:rt", "access_token:at"), gateway.revoked);
  }

  @Test
  void reachesAProviderAnOperatorHasDisabled() {
    FixedResolver resolver = new FixedResolver(configuration(REVOCATION_ENDPOINT));
    new LinkedExternalAccountRevoker(resolver, new RecordingGateway(), aesCipher)
        .revoke(TENANT, account("at", "rt"));

    assertTrue(resolver.resolvedIncludingDisabled);
  }

  @Test
  void skipsAMissingRefreshToken() {
    RecordingGateway gateway = new RecordingGateway();
    LinkedExternalAccountRevoker revoker =
        new LinkedExternalAccountRevoker(
            new FixedResolver(configuration(REVOCATION_ENDPOINT)), gateway, aesCipher);

    assertTrue(revoker.revoke(TENANT, account("at", null)));
    assertEquals(List.of("access_token:at"), gateway.revoked);
  }

  @Test
  void reportsFalseWithoutCallingWhenNoRevocationEndpoint() {
    RecordingGateway gateway = new RecordingGateway();
    LinkedExternalAccountRevoker revoker =
        new LinkedExternalAccountRevoker(
            new FixedResolver(configuration(null)), gateway, aesCipher);

    assertFalse(revoker.revoke(TENANT, account("at", "rt")));
    assertTrue(gateway.revoked.isEmpty());
  }

  @Test
  void reportsFalseWhenConfigurationIsGone() {
    RecordingGateway gateway = new RecordingGateway();
    LinkedExternalAccountRevoker revoker =
        new LinkedExternalAccountRevoker(new FixedResolver(null), gateway, aesCipher);

    assertFalse(revoker.revoke(TENANT, account("at", "rt")));
    assertTrue(gateway.revoked.isEmpty());
  }

  @Test
  void reportsFalseWhenTheProviderRefuses() {
    RecordingGateway gateway = new RecordingGateway();
    gateway.accept = false;
    LinkedExternalAccountRevoker revoker =
        new LinkedExternalAccountRevoker(
            new FixedResolver(configuration(REVOCATION_ENDPOINT)), gateway, aesCipher);

    assertFalse(revoker.revoke(TENANT, account("at", "rt")));
    assertEquals(2, gateway.revoked.size());
  }

  @Test
  void neverThrowsWhenTheGatewayFails() {
    RecordingGateway gateway = new RecordingGateway();
    gateway.failure = new IllegalStateException("boom");
    LinkedExternalAccountRevoker revoker =
        new LinkedExternalAccountRevoker(
            new FixedResolver(configuration(REVOCATION_ENDPOINT)), gateway, aesCipher);

    assertFalse(revoker.revoke(TENANT, account("at", "rt")));
  }

  @Test
  void neverThrowsWhenATokenCannotBeDecrypted() {
    RecordingGateway gateway = new RecordingGateway();
    LinkedExternalAccountRevoker revoker =
        new LinkedExternalAccountRevoker(
            new FixedResolver(configuration(REVOCATION_ENDPOINT)), gateway, aesCipher);
    LinkedExternalAccount broken =
        new LinkedExternalAccount.Builder()
            .userIdentifier(USER)
            .provider(PROVIDER)
            .encryptedAccessToken(
                new SealedData(
                    SealedData.VERSION_1,
                    "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
                    "AAAAAAAAAAAAAAAA",
                    "bm90LWNpcGhlcg=="))
            .build();

    assertFalse(revoker.revoke(TENANT, broken));
    assertTrue(gateway.revoked.isEmpty());
  }

  @Test
  void revokesTheTokensOfAParkedSession() {
    RecordingGateway gateway = new RecordingGateway();
    LinkedExternalAccountRevoker revoker =
        new LinkedExternalAccountRevoker(
            new FixedResolver(configuration(REVOCATION_ENDPOINT)), gateway, aesCipher);

    assertTrue(revoker.revoke(TENANT, session(AccountLinkingSessionStatus.PARKED)));
    assertEquals(List.of("refresh_token:parked-rt", "access_token:parked-at"), gateway.revoked);
  }

  @Test
  void leavesAConsumedSessionsTokensToTheLinkedAccount() {
    RecordingGateway gateway = new RecordingGateway();
    LinkedExternalAccountRevoker revoker =
        new LinkedExternalAccountRevoker(
            new FixedResolver(configuration(REVOCATION_ENDPOINT)), gateway, aesCipher);

    assertTrue(revoker.revoke(TENANT, session(AccountLinkingSessionStatus.CONSUMED)));
    assertTrue(gateway.revoked.isEmpty());
  }

  @Test
  void hasNothingToRevokeBeforeTheCallback() {
    RecordingGateway gateway = new RecordingGateway();
    LinkedExternalAccountRevoker revoker =
        new LinkedExternalAccountRevoker(
            new FixedResolver(configuration(REVOCATION_ENDPOINT)), gateway, aesCipher);
    AccountLinkingSession pending =
        new AccountLinkingSession.Builder()
            .provider(PROVIDER)
            .status(AccountLinkingSessionStatus.PENDING)
            .build();

    assertTrue(revoker.revoke(TENANT, pending));
    assertTrue(gateway.revoked.isEmpty());
  }

  @Test
  void revokesTheTokensARelinkReplaced() {
    RecordingGateway gateway = new RecordingGateway();
    LinkedExternalAccountRevoker revoker =
        new LinkedExternalAccountRevoker(
            new FixedResolver(configuration(REVOCATION_ENDPOINT)), gateway, aesCipher);

    assertTrue(
        revoker.revokeReplaced(TENANT, account("old-at", "old-rt"), account("new-at", "new-rt")));
    assertEquals(List.of("refresh_token:old-rt", "access_token:old-at"), gateway.revoked);
  }

  @Test
  void keepsARefreshTokenTheProviderHandedBackUnchanged() {
    RecordingGateway gateway = new RecordingGateway();
    LinkedExternalAccountRevoker revoker =
        new LinkedExternalAccountRevoker(
            new FixedResolver(configuration(REVOCATION_ENDPOINT)), gateway, aesCipher);

    assertTrue(revoker.revokeReplaced(TENANT, account("old-at", "rt"), account("new-at", "rt")));
    assertEquals(List.of("access_token:old-at"), gateway.revoked);
  }

  @Test
  void revokesNothingWhenTheRenewedTokensCannotBeRead() {
    RecordingGateway gateway = new RecordingGateway();
    LinkedExternalAccountRevoker revoker =
        new LinkedExternalAccountRevoker(
            new FixedResolver(configuration(REVOCATION_ENDPOINT)), gateway, aesCipher);
    LinkedExternalAccount unreadable =
        new LinkedExternalAccount.Builder()
            .userIdentifier(USER)
            .provider(PROVIDER)
            .encryptedAccessToken(
                new SealedData(
                    SealedData.VERSION_1,
                    "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
                    "AAAAAAAAAAAAAAAA",
                    "bm90LWNpcGhlcg=="))
            .build();

    assertFalse(revoker.revokeReplaced(TENANT, account("old-at", "old-rt"), unreadable));
    assertTrue(gateway.revoked.isEmpty());
  }
}
