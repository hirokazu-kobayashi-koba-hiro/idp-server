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
import org.idp.server.federation.sso.oidc.OidcSsoConfiguration;
import org.idp.server.federation.sso.oidc.OidcTokenResult;

/**
 * When the external tokens stored for a link are taken to expire.
 *
 * <p>Applied the same way when a link is made and when its tokens are refreshed, so the two cannot
 * drift apart again.
 */
public class ExternalTokenLifetime {

  /**
   * Assumed access token lifetime when the provider sends no {@code expires_in}.
   *
   * <p>{@code expires_in} is only RECOMMENDED (RFC 6749 §5.1). A token stored without an expiry
   * would count as expired from the start and be refreshed on every retrieval; one that dies
   * earlier is refreshed, or reported as needing a new link, when it is next used.
   */
  static final long DEFAULT_ACCESS_TOKEN_LIFETIME_SECONDS = 3600;

  public static LocalDateTime accessTokenExpiresAt(OidcTokenResult tokenResult, LocalDateTime now) {
    long lifetime =
        tokenResult.hasExpiresIn()
            ? tokenResult.expiresIn()
            : DEFAULT_ACCESS_TOKEN_LIFETIME_SECONDS;
    return now.plusSeconds(lifetime);
  }

  /**
   * The refresh token's expiry, or {@code null} when it is not known.
   *
   * <p>Token responses do not say when a refresh token expires, so the provider's configured {@code
   * refresh_token_expires_in} is used. Without one, the expiry is left unknown rather than set to
   * now: the refresh is then attempted, and the provider says whether the token still works.
   */
  public static LocalDateTime refreshTokenExpiresAt(
      OidcSsoConfiguration configuration, LocalDateTime now) {
    long lifetime = configuration.refreshTokenExpiresIn();
    return lifetime > 0 ? now.plusSeconds(lifetime) : null;
  }
}
