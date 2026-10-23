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

import org.idp.server.account_linking.gateway.ExternalIdpTokenGateway;
import org.idp.server.core.openid.federation.FederationConfigurationNotFoundException;
import org.idp.server.platform.crypto.SealedData;
import org.idp.server.platform.crypto.datakey.TenantDataCipher;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

/**
 * Asks the external IdP to revoke the tokens stored for one linked account.
 *
 * <p>Used when a link is removed, whether the user removes it or the user is deleted. Removing the
 * row only stops this server from using the tokens; until the external IdP revokes them, anyone who
 * copied them can still use them.
 *
 * <p>A provider an operator has disabled is still reached: disabling stops new links, not the
 * cleanup of tokens already issued under it.
 *
 * <p>Best effort throughout. The link is removed even when the provider has no revocation endpoint,
 * its configuration is gone, or it cannot be reached: keeping the link because the external side
 * failed would leave the user unable to undo it.
 */
public class LinkedExternalAccountRevoker {

  AccountLinkingConfigurationResolver configurationResolver;
  ExternalIdpTokenGateway tokenGateway;
  TenantDataCipher tokenCipher;
  LoggerWrapper log = LoggerWrapper.getLogger(LinkedExternalAccountRevoker.class);

  public LinkedExternalAccountRevoker(
      AccountLinkingConfigurationResolver configurationResolver,
      ExternalIdpTokenGateway tokenGateway,
      TenantDataCipher tokenCipher) {
    this.configurationResolver = configurationResolver;
    this.tokenGateway = tokenGateway;
    this.tokenCipher = tokenCipher;
  }

  /**
   * Revokes the account's tokens at the external IdP.
   *
   * @return whether every stored token was revoked
   */
  public boolean revoke(Tenant tenant, LinkedExternalAccount account) {
    return revoke(
        tenant,
        account.provider(),
        account.encryptionKeyId(),
        account.encryptedAccessToken(),
        account.encryptedRefreshToken());
  }

  /**
   * Revokes the tokens a linking session holds after its callback, before anyone completed it.
   *
   * <p>Only a parked session holds tokens of its own. A consumed one has handed them to a linked
   * account, and revoking them here would cut off that account; it is cleaned up through {@link
   * #revoke(Tenant, LinkedExternalAccount)} instead.
   *
   * @return whether every parked token was revoked; {@code true} when there was nothing to revoke
   */
  public boolean revoke(Tenant tenant, AccountLinkingSession session) {
    if (!session.status().isParked()
        || session.parkedCredentials() == null
        || !session.parkedCredentials().exists()) {
      return true;
    }
    ParkedCredentials credentials = session.parkedCredentials();
    return revoke(
        tenant,
        session.provider(),
        credentials.encryptionKeyId(),
        credentials.encryptedAccessToken(),
        credentials.encryptedRefreshToken());
  }

  /**
   * The refresh token goes first: per RFC 7009 its revocation should also invalidate the access
   * tokens issued from it, so the second call is only for providers that do not do that.
   */
  private boolean revoke(
      Tenant tenant,
      ExternalIdpProvider provider,
      String encryptionKeyId,
      SealedData encryptedAccessToken,
      SealedData encryptedRefreshToken) {
    AccountLinkingProviderConfiguration configuration;
    try {
      configuration = configurationResolver.resolveIncludingDisabled(tenant, provider);
    } catch (FederationConfigurationNotFoundException exception) {
      log.warn(
          "Account linking provider configuration not found; tokens left unrevoked: provider={}",
          provider.value());
      return false;
    }
    if (!configuration.hasRevocationEndpoint()) {
      log.info(
          "Account linking provider has no revocation endpoint; tokens left unrevoked: provider={}",
          provider.value());
      return false;
    }

    boolean refreshRevoked =
        revoke(tenant, configuration, encryptionKeyId, encryptedRefreshToken, "refresh_token");
    boolean accessRevoked =
        revoke(tenant, configuration, encryptionKeyId, encryptedAccessToken, "access_token");
    return refreshRevoked && accessRevoked;
  }

  private boolean revoke(
      Tenant tenant,
      AccountLinkingProviderConfiguration configuration,
      String encryptionKeyId,
      SealedData encryptedToken,
      String tokenTypeHint) {
    if (encryptedToken == null) {
      return true;
    }
    try {
      String token = tokenCipher.open(tenant, encryptionKeyId, encryptedToken);
      return tokenGateway.revoke(
          configuration.oidc(), configuration.revocationEndpoint(), token, tokenTypeHint);
    } catch (RuntimeException exception) {
      log.warn("Account linking token could not be revoked: {}", exception.getMessage());
      return false;
    }
  }
}
