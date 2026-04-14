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

package org.idp.server.core.openid.token.plugin;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.openid.federation.sso.SsoCredentials;
import org.idp.server.core.openid.federation.sso.SsoCredentialsQueryRepository;
import org.idp.server.core.openid.grant_management.grant.AuthorizationGrant;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.oauth.clientauthenticator.clientcredentials.ClientCredentials;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfiguration;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

/**
 * Embeds SSO credentials (external IdP access token) into the access token custom claims.
 *
 * <p>When a user has SSO credentials from a federation login, this creator adds the external IdP's
 * access token as a custom claim. The claim is stored in DB and returned via Token Introspection.
 *
 * <p><b>Security constraint:</b> This creator is only active when the access token type is
 * <b>opaque (identifier)</b>. For JWT access tokens, the external token would be exposed in the JWT
 * payload in plaintext, which is a security risk. With opaque tokens, the custom claims are stored
 * in DB only and returned exclusively via the Token Introspection endpoint over TLS.
 *
 * <p><b>Activation:</b> Set {@code access_token_sso_credentials: true} in
 * authorization_server.extension and use opaque access token type (default).
 *
 * <p><b>Migration use case:</b> When migrating from a legacy ID service, the backend can retrieve
 * the legacy access token via Introspection and use it for authorization checks against the legacy
 * system, while users authenticate via idp-server (FIDO, eKYC, etc.).
 */
public class SsoCredentialsReferenceClaimsCreator implements AccessTokenCustomClaimsCreator {

  SsoCredentialsQueryRepository ssoCredentialsQueryRepository;
  LoggerWrapper log = LoggerWrapper.getLogger(SsoCredentialsReferenceClaimsCreator.class);

  public SsoCredentialsReferenceClaimsCreator(
      SsoCredentialsQueryRepository ssoCredentialsQueryRepository) {
    this.ssoCredentialsQueryRepository = ssoCredentialsQueryRepository;
  }

  @Override
  public boolean shouldCreate(
      Tenant tenant,
      AuthorizationGrant authorizationGrant,
      AuthorizationServerConfiguration authorizationServerConfiguration,
      ClientConfiguration clientConfiguration,
      ClientCredentials clientCredentials) {

    if (!authorizationServerConfiguration.enabledAccessTokenSsoCredentials()) {
      return false;
    }

    // Security: only allow with opaque access tokens to prevent token exposure in JWT payload
    if (!authorizationServerConfiguration.isIdentifierAccessTokenType()) {
      log.warn(
          "SSO credentials in access token is only supported with opaque (identifier) access token type. "
              + "Current type is JWT. Skipping to prevent external token exposure.");
      return false;
    }

    User user = authorizationGrant.user();
    return user != null && user.exists();
  }

  @Override
  public Map<String, Object> create(
      Tenant tenant,
      AuthorizationGrant authorizationGrant,
      AuthorizationServerConfiguration authorizationServerConfiguration,
      ClientConfiguration clientConfiguration,
      ClientCredentials clientCredentials) {

    User user = authorizationGrant.user();
    SsoCredentials ssoCredentials = ssoCredentialsQueryRepository.find(tenant, user);

    if (!ssoCredentials.exists()) {
      return Map.of();
    }

    Map<String, Object> claims = new HashMap<>();
    claims.put("sso_provider", ssoCredentials.provider());
    claims.put("sso_access_token", ssoCredentials.accessToken());

    log.debug(
        "SSO credentials embedded in access token: provider={}, user={}",
        ssoCredentials.provider(),
        user.sub());

    return claims;
  }
}
