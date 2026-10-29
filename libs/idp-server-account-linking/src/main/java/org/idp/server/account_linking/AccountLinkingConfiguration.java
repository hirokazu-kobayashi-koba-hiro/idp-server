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

import org.idp.server.platform.json.JsonReadable;

/**
 * Linking-specific settings read from the same {@code federation_configurations} payload the OIDC
 * client settings come from.
 *
 * <p>A separate configuration type is what this should eventually use: login and linking want
 * different scopes and different redirect URIs. Until that exists, only the fields linking adds on
 * top of the OIDC ones are read here.
 */
public class AccountLinkingConfiguration implements JsonReadable {

  public static final long DEFAULT_REFRESH_BEFORE_EXPIRY_SECONDS = 30;

  String duplicateLinkPolicy;
  boolean multipleAccounts;
  boolean revokeOnRelink;
  Long refreshBeforeExpirySeconds;
  String revocationEndpoint;

  public AccountLinkingConfiguration() {}

  public DuplicateLinkPolicy duplicateLinkPolicy() {
    return DuplicateLinkPolicy.of(duplicateLinkPolicy);
  }

  /**
   * Whether a user may link more than one account of this provider.
   *
   * <p>Off by default: a user holds one link per provider, and linking again replaces it.
   */
  public boolean allowsMultipleAccounts() {
    return multipleAccounts;
  }

  /**
   * Whether linking an account again revokes the tokens it replaces at the external IdP.
   *
   * <p>Off by default. RFC 7009 lets a provider revoke every token of the grant along with the one
   * named, and linking again usually lands in the same grant, so on such a provider the tokens just
   * obtained would stop working too. It is for providers known to revoke only the token named.
   */
  public boolean revokesOnRelink() {
    return revokeOnRelink;
  }

  /**
   * How many seconds before its expiry a stored access token is refreshed before it is handed out.
   *
   * <p>30 when not set. A token released seconds before it expires fails at the external API the
   * client calls next.
   */
  public long refreshBeforeExpirySeconds() {
    return refreshBeforeExpirySeconds == null
        ? DEFAULT_REFRESH_BEFORE_EXPIRY_SECONDS
        : refreshBeforeExpirySeconds;
  }

  /**
   * The external IdP's token revocation endpoint (RFC 7009), used when a link is removed, and when
   * it is renewed with {@link #revokesOnRelink()} on.
   */
  public String revocationEndpoint() {
    return revocationEndpoint;
  }

  public boolean hasRevocationEndpoint() {
    return revocationEndpoint != null && !revocationEndpoint.isBlank();
  }
}
