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

package org.idp.server.core.openid.oauth.verifier;

import org.idp.server.core.openid.oauth.AuthorizationProfile;
import org.idp.server.core.openid.oauth.OAuthRequestContext;
import org.idp.server.core.openid.oauth.clientauthenticator.clientcredentials.ClientCredentials;

/** AuthorizationRequestVerifier */
public interface AuthorizationRequestVerifier {

  AuthorizationProfile profile();

  void verify(OAuthRequestContext oAuthRequestContext);

  /**
   * Profile-aware verification with client credentials.
   *
   * <p>Used at PAR endpoint where the parsed client_assertion JWT is available via {@link
   * ClientCredentials#clientAssertionJwt()}. Default implementation delegates to {@link
   * #verify(OAuthRequestContext)} so existing verifiers do not need to opt-in. Profiles that need
   * to inspect the client assertion (e.g. FAPI 2.0 §5.3.2.1-2.8 / §5.4) should override.
   *
   * @param oAuthRequestContext the request context
   * @param clientCredentials the authenticated client credentials, may be {@code null} when client
   *     authentication has not yet occurred (regular Authorization Endpoint flow)
   */
  default void verify(
      OAuthRequestContext oAuthRequestContext, ClientCredentials clientCredentials) {
    verify(oAuthRequestContext);
  }
}
