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
import org.idp.server.account_linking.exception.ExternalIdpRequestFailedException;
import org.idp.server.account_linking.gateway.ExternalIdpIdTokenGateway;
import org.idp.server.account_linking.gateway.ExternalIdpTokenGateway;
import org.idp.server.account_linking.gateway.ExternalIdpUserinfoGateway;
import org.idp.server.core.openid.identity.User;
import org.idp.server.federation.sso.oidc.OidcSsoConfiguration;
import org.idp.server.federation.sso.oidc.OidcTokenResult;
import org.idp.server.platform.crypto.SealedData;
import org.idp.server.platform.crypto.datakey.TenantDataCipher;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

/**
 * Turns the authorization code a callback carries into the credentials held against the session.
 *
 * <p>Redeems the code, reads who the external account is, and encrypts the tokens under the
 * tenant's data key, bound to the user and provider of the link.
 */
public class AccountLinkingCredentialsService {

  AccountLinkingConfigurationResolver configurationResolver;
  ExternalIdpTokenGateway tokenGateway;
  ExternalIdpIdTokenGateway idTokenGateway;
  ExternalIdpUserinfoGateway userinfoGateway;
  TenantDataCipher tokenCipher;
  LoggerWrapper log = LoggerWrapper.getLogger(AccountLinkingCredentialsService.class);

  public AccountLinkingCredentialsService(
      AccountLinkingConfigurationResolver configurationResolver,
      ExternalIdpTokenGateway tokenGateway,
      ExternalIdpIdTokenGateway idTokenGateway,
      ExternalIdpUserinfoGateway userinfoGateway,
      TenantDataCipher tokenCipher) {
    this.configurationResolver = configurationResolver;
    this.tokenGateway = tokenGateway;
    this.idTokenGateway = idTokenGateway;
    this.userinfoGateway = userinfoGateway;
    this.tokenCipher = tokenCipher;
  }

  /**
   * @throws ExternalIdpRequestFailedException when the external IdP refuses the code, the id_token
   *     does not verify, or the userinfo request fails
   */
  public ParkedCredentials obtain(
      Tenant tenant, AccountLinkingSession session, String code, LocalDateTime now) {

    OidcSsoConfiguration configuration =
        configurationResolver.resolve(tenant, session.provider()).oidc();

    OidcTokenResult tokenResult =
        tokenGateway.exchangeAuthorizationCode(
            configuration, code, configuration.redirectUri(), session.codeVerifier());

    if (tokenResult.isError() || !tokenResult.hasAccessToken()) {
      log.warn(
          "Account linking token exchange failed. provider={}, status={}",
          session.provider().value(),
          tokenResult.statusCode());
      throw new ExternalIdpRequestFailedException(
          "Account linking failed at the external identity provider token endpoint.");
    }

    ExternalAccountIdentity identity = identity(configuration, session, tokenResult);

    TenantDataCipher.Sealer sealer = tokenCipher.sealer(tenant);
    SealedData accessToken =
        sealer.seal(
            tokenResult.accessToken(),
            AccountLinkingTokenCipher.accessToken(session.userIdentifier(), session.provider()));
    SealedData refreshToken =
        tokenResult.hasRefreshToken()
            ? sealer.seal(
                tokenResult.refreshToken(),
                AccountLinkingTokenCipher.refreshToken(
                    session.userIdentifier(), session.provider()))
            : null;

    return new ParkedCredentials.Builder()
        .federatedUserId(identity.federatedUserId())
        .federatedUsername(identity.federatedUsername())
        .grantedScope(tokenResult.scopeOr(session.scopeAskedFor(configuration)))
        .encryptedAccessToken(accessToken)
        .encryptedRefreshToken(refreshToken)
        .encryptionKeyId(sealer.keyId())
        .accessTokenExpiresAt(ExternalTokenLifetime.accessTokenExpiresAt(tokenResult, now))
        .refreshTokenExpiresAt(
            refreshToken == null
                ? null
                : ExternalTokenLifetime.refreshTokenExpiresAt(configuration, now))
        .build();
  }

  /**
   * Asks the provider who the account is: the id_token when it issued one, the userinfo when set.
   */
  private ExternalAccountIdentity identity(
      OidcSsoConfiguration configuration,
      AccountLinkingSession session,
      OidcTokenResult tokenResult) {
    String verifiedSubject =
        tokenResult.hasIdToken()
            ? idTokenGateway.verifiedSubject(configuration, session, tokenResult)
            : null;
    User federatedUser =
        configuration.hasUserinfo()
            ? userinfoGateway.request(configuration, tokenResult.accessToken())
            : null;
    return ExternalAccountIdentity.of(verifiedSubject, federatedUser);
  }
}
