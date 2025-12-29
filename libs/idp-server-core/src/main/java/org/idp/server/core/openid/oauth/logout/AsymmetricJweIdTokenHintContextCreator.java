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

package org.idp.server.core.openid.oauth.logout;

import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfigurationQueryRepository;
import org.idp.server.core.openid.oauth.exception.OAuthBadRequestException;
import org.idp.server.core.openid.oauth.request.OAuthLogoutParameters;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

/**
 * AsymmetricJweIdTokenHintContextCreator
 *
 * <p>Handles asymmetrically encrypted JWT (JWE) id_token_hint. This pattern is NOT supported
 * because the OP cannot decrypt JWE encrypted with the client's public key (the OP does not have
 * the client's private key).
 *
 * <p>OpenID Connect RP-Initiated Logout 1.0 only supports symmetrically encrypted id_token_hint
 * where the OP can derive the decryption key from the client_secret.
 *
 * @see <a href="https://openid.net/specs/openid-connect-rpinitiated-1_0.html">RP-Initiated
 *     Logout</a>
 */
public class AsymmetricJweIdTokenHintContextCreator implements IdTokenHintContextCreator {

  @Override
  public IdTokenHintResult create(
      Tenant tenant,
      OAuthLogoutParameters parameters,
      AuthorizationServerConfiguration serverConfiguration,
      ClientConfigurationQueryRepository clientConfigurationQueryRepository) {

    throw new OAuthBadRequestException(
        "invalid_request",
        "asymmetrically encrypted id_token_hint is not supported; "
            + "the OP cannot decrypt JWE encrypted with the client's public key",
        tenant);
  }
}
