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
import org.idp.server.account_linking.exception.LinkedExternalAccountTokenException;
import org.idp.server.account_linking.gateway.ExternalIdpTokenGateway;
import org.idp.server.account_linking.repository.LinkedExternalAccountCommandRepository;
import org.idp.server.account_linking.repository.LinkedExternalAccountQueryRepository;
import org.idp.server.core.openid.federation.FederationConfigurationNotFoundException;
import org.idp.server.federation.sso.oidc.OidcSsoConfiguration;
import org.idp.server.federation.sso.oidc.OidcTokenResult;
import org.idp.server.platform.crypto.SealedData;
import org.idp.server.platform.crypto.datakey.SealingContext;
import org.idp.server.platform.crypto.datakey.TenantDataCipher;
import org.idp.server.platform.date.SystemDateTime;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

/**
 * Turns a stored linked account into an access token that can be used now.
 *
 * <p>Decrypts the stored access token, refreshing it first when it is expired or about to be, and
 * moves the stored tokens to the active data key when they are still under an older one. Who may
 * ask is not decided here: the caller has already established that, so the same steps serve the
 * token endpoint and any other entry point that needs the external token.
 *
 * <p>The refresh token is spent only here and never returned. An application that spent it would
 * rotate the stored one out from under this server.
 */
public class LinkedExternalAccountTokenService {

  LinkedExternalAccountQueryRepository accountQueryRepository;
  LinkedExternalAccountCommandRepository accountCommandRepository;
  AccountLinkingConfigurationResolver configurationResolver;
  ExternalIdpTokenGateway tokenGateway;
  TenantDataCipher tokenCipher;
  LoggerWrapper log = LoggerWrapper.getLogger(LinkedExternalAccountTokenService.class);

  public LinkedExternalAccountTokenService(
      LinkedExternalAccountQueryRepository accountQueryRepository,
      LinkedExternalAccountCommandRepository accountCommandRepository,
      AccountLinkingConfigurationResolver configurationResolver,
      ExternalIdpTokenGateway tokenGateway,
      TenantDataCipher tokenCipher) {
    this.accountQueryRepository = accountQueryRepository;
    this.accountCommandRepository = accountCommandRepository;
    this.configurationResolver = configurationResolver;
    this.tokenGateway = tokenGateway;
    this.tokenCipher = tokenCipher;
  }

  /**
   * The account's access token, refreshed or re-encrypted first when needed.
   *
   * <p>The token is refreshed when it expires within the provider's {@code
   * refresh_before_expiry_seconds}, or always when {@code forceRefresh} is set.
   *
   * @param account the account as read without a lock; it is read again under its row lock before
   *     anything is written
   * @param forceRefresh refresh even when the stored token is still usable
   * @throws LinkedExternalAccountTokenException when the account is gone, the token cannot be
   *     refreshed and the user has to link again, or the external IdP is unavailable
   */
  public ExternalAccessTokenAcquisition acquire(
      Tenant tenant, LinkedExternalAccount account, boolean forceRefresh) {

    long refreshBeforeExpirySeconds = refreshBeforeExpirySeconds(tenant, account);
    if (!forceRefresh && isUsable(account, SystemDateTime.now(), refreshBeforeExpirySeconds)) {
      LinkedExternalAccount current =
          tokenCipher.isBehindActive(tenant, account.encryptionKeyId())
              ? reencryptUnderLock(tenant, account)
              : account;
      return opened(tenant, current, false);
    }

    LinkedExternalAccount refreshed =
        refreshUnderLock(tenant, account, forceRefresh, refreshBeforeExpirySeconds);
    return opened(tenant, refreshed, true);
  }

  private boolean isUsable(
      LinkedExternalAccount account, LocalDateTime now, long refreshBeforeExpirySeconds) {
    return !account.isAccessTokenExpired(now.plusSeconds(refreshBeforeExpirySeconds));
  }

  /**
   * The provider's {@code refresh_before_expiry_seconds}.
   *
   * <p>Read even when the provider is disabled: a token still within its expiry is handed out as
   * before, and only a refresh needs the provider enabled. A provider that is gone falls back to
   * the default, and its refresh is refused as unavailable.
   */
  private long refreshBeforeExpirySeconds(Tenant tenant, LinkedExternalAccount account) {
    try {
      return configurationResolver
          .resolveIncludingDisabled(tenant, account.provider())
          .refreshBeforeExpirySeconds();
    } catch (FederationConfigurationNotFoundException exception) {
      return AccountLinkingConfiguration.DEFAULT_REFRESH_BEFORE_EXPIRY_SECONDS;
    }
  }

  /**
   * Refreshes the stored token with the row locked.
   *
   * <p>Concurrent requests for the same account wait here, and the ones that get the lock after a
   * refresh find a usable token and return it. Without the lock both would spend the same refresh
   * token, and a provider that rotates refresh tokens would revoke the grant for the loser.
   *
   * <p>A forced refresh is skipped the same way when another request put a new token in place while
   * this one waited: clients that all got a 401 from the external API and retry at once reach the
   * external IdP once, not once each.
   */
  private LinkedExternalAccount refreshUnderLock(
      Tenant tenant,
      LinkedExternalAccount account,
      boolean forceRefresh,
      long refreshBeforeExpirySeconds) {

    LinkedExternalAccount locked = lockOrNotFound(tenant, account);
    LocalDateTime now = SystemDateTime.now();
    boolean alreadyRefreshed =
        forceRefresh
            ? locked.hasNewAccessTokenSince(account)
            : isUsable(locked, now, refreshBeforeExpirySeconds);
    if (alreadyRefreshed) {
      return locked;
    }

    if (!locked.hasRefreshToken() || locked.isRefreshTokenExpired(now)) {
      log.warn(
          "Stored external token cannot be refreshed. provider={}, alias={}, forced={}",
          locked.provider().value(),
          locked.accountAlias().value(),
          forceRefresh);
      throw LinkedExternalAccountTokenException.relinkRequired(
          relinkRequiredDescription(forceRefresh));
    }

    OidcSsoConfiguration configuration = providerConfiguration(tenant, locked);
    String storedRefreshToken =
        tokenCipher.open(
            tenant,
            locked.encryptionKeyId(),
            locked.encryptedRefreshToken(),
            refreshContext(locked));
    OidcTokenResult tokenResult = tokenGateway.refresh(configuration, storedRefreshToken);

    if (tokenResult.isError() || !tokenResult.hasAccessToken()) {
      throw refreshFailure(locked, tokenResult);
    }

    LinkedExternalAccount refreshed =
        withRefreshedTokens(tenant, locked, configuration, tokenResult, storedRefreshToken, now);
    accountCommandRepository.update(tenant, refreshed);
    return refreshed;
  }

  /**
   * Moves the stored tokens to the active key, with the row locked.
   *
   * <p>Done the first time a token stored under an older key is read after a rotation, so that an
   * account in use stops depending on the old key without a batch job. The lock keeps this from
   * writing back a refresh token that a concurrent refresh has just replaced.
   */
  private LinkedExternalAccount reencryptUnderLock(Tenant tenant, LinkedExternalAccount account) {

    LinkedExternalAccount locked = lockOrNotFound(tenant, account);
    if (!tokenCipher.isBehindActive(tenant, locked.encryptionKeyId())) {
      return locked;
    }

    String keyId = locked.encryptionKeyId();
    TenantDataCipher.Sealer sealer = tokenCipher.sealer(tenant);
    SealedData accessToken =
        sealer.seal(
            tokenCipher.open(tenant, keyId, locked.encryptedAccessToken(), accessContext(locked)),
            accessContext(locked));
    SealedData refreshToken =
        locked.hasRefreshToken()
            ? sealer.seal(
                tokenCipher.open(
                    tenant, keyId, locked.encryptedRefreshToken(), refreshContext(locked)),
                refreshContext(locked))
            : null;

    LinkedExternalAccount reencrypted =
        locked.withReencryptedTokens(
            accessToken, refreshToken, sealer.keyId(), SystemDateTime.now());
    accountCommandRepository.update(tenant, reencrypted);
    log.info(
        "Stored external tokens moved to the current data key. provider={}, alias={}, from={}, to={}",
        locked.provider().value(),
        locked.accountAlias().value(),
        keyId,
        sealer.keyId());
    return reencrypted;
  }

  /**
   * The account, read again under its row lock.
   *
   * <p>It was read without the lock first, so an unlink or a deletion may have removed it since.
   */
  private LinkedExternalAccount lockOrNotFound(Tenant tenant, LinkedExternalAccount account) {
    LinkedExternalAccount locked =
        accountQueryRepository.lock(tenant, account.userIdentifier(), account.accountAlias());
    if (!locked.exists()) {
      throw LinkedExternalAccountTokenException.notFound("Linked external account not found.");
    }
    return locked;
  }

  /**
   * The provider's settings for a refresh.
   *
   * <p>An operator may have removed or disabled the provider since the account was linked. The
   * stored refresh token cannot be spent without them. Linking again would fail as well while the
   * provider is gone, so the caller is told the provider is unavailable rather than to link again.
   */
  private OidcSsoConfiguration providerConfiguration(Tenant tenant, LinkedExternalAccount account) {
    try {
      return configurationResolver.resolve(tenant, account.provider()).oidc();
    } catch (FederationConfigurationNotFoundException exception) {
      log.warn(
          "Linking provider is no longer configured. provider={}, alias={}",
          account.provider().value(),
          account.accountAlias().value());
      throw LinkedExternalAccountTokenException.providerUnavailable(
          "The linking provider is no longer available.");
    }
  }

  /** A forced refresh may be refused while the stored token has not expired yet. */
  private static String relinkRequiredDescription(boolean forceRefresh) {
    return forceRefresh
        ? "The stored token cannot be refreshed. Link the account again."
        : "The stored token has expired and cannot be refreshed. Link the account again.";
  }

  private LinkedExternalAccountTokenException refreshFailure(
      LinkedExternalAccount account, OidcTokenResult tokenResult) {
    log.warn(
        "Stored external token refresh failed. provider={}, alias={}, status={}",
        account.provider().value(),
        account.accountAlias().value(),
        tokenResult.statusCode());

    return LinkedExternalAccountTokenException.refreshFailed(tokenResult.statusCode());
  }

  private LinkedExternalAccount withRefreshedTokens(
      Tenant tenant,
      LinkedExternalAccount account,
      OidcSsoConfiguration configuration,
      OidcTokenResult tokenResult,
      String storedRefreshToken,
      LocalDateTime now) {

    TenantDataCipher.Sealer sealer = tokenCipher.sealer(tenant);
    SealedData accessToken = sealer.seal(tokenResult.accessToken(), accessContext(account));
    LocalDateTime accessTokenExpiresAt =
        ExternalTokenLifetime.accessTokenExpiresAt(tokenResult, now);

    // A provider that does not rotate answers without a refresh token; the stored one stays valid.
    // It is encrypted again all the same, so that the row's one key id opens both tokens.
    if (!tokenResult.hasRefreshToken()) {
      return account.withRefreshedTokens(
          accessToken,
          sealer.seal(storedRefreshToken, refreshContext(account)),
          sealer.keyId(),
          accessTokenExpiresAt,
          account.refreshTokenExpiresAt(),
          now);
    }

    return account.withRefreshedTokens(
        accessToken,
        sealer.seal(tokenResult.refreshToken(), refreshContext(account)),
        sealer.keyId(),
        accessTokenExpiresAt,
        ExternalTokenLifetime.refreshTokenExpiresAt(configuration, now),
        now);
  }

  private ExternalAccessTokenAcquisition opened(
      Tenant tenant, LinkedExternalAccount account, boolean refreshed) {
    String accessToken =
        tokenCipher.open(
            tenant,
            account.encryptionKeyId(),
            account.encryptedAccessToken(),
            accessContext(account));
    return new ExternalAccessTokenAcquisition(
        new ExternalAccessToken(accessToken, account.accessTokenExpiresAt(), account.scope()),
        refreshed);
  }

  private static SealingContext accessContext(LinkedExternalAccount account) {
    return AccountLinkingTokenCipher.accessToken(account.userIdentifier(), account.provider());
  }

  private static SealingContext refreshContext(LinkedExternalAccount account) {
    return AccountLinkingTokenCipher.refreshToken(account.userIdentifier(), account.provider());
  }
}
