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

package org.idp.server.core.openid.extension.attestation;

import org.idp.server.core.openid.oauth.clientauthenticator.BackchannelRequestContext;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfiguration;
import org.idp.server.platform.jose.JsonWebSignatureHeader;

/**
 * Resolves the trusted Client Attester keys from the static client configuration ({@code
 * client_attestation_jwks}).
 *
 * <p>This is the Attester model: the client registers the JWKS of its trusted Client Attester(s)
 * beforehand, and every Client Attestation JWT of that client must be signed by one of those keys.
 */
public class StaticJwksClientAttestationKeyResolver implements ClientAttestationKeyResolver {

  @Override
  public String resolveJwks(BackchannelRequestContext context, JsonWebSignatureHeader header) {
    ClientConfiguration clientConfiguration = context.clientConfiguration();
    if (!clientConfiguration.hasClientAttestationJwks()) {
      return null;
    }
    return clientConfiguration.clientAttestationJwks();
  }
}
