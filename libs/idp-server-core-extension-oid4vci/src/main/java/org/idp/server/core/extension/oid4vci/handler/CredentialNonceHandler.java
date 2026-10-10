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
package org.idp.server.core.extension.oid4vci.handler;

import org.idp.server.core.extension.oid4vci.io.CredentialNonceResponse;
import org.idp.server.core.extension.oid4vci.nonce.CredentialNonce;
import org.idp.server.core.extension.oid4vci.nonce.CredentialNonceIssuer;
import org.idp.server.core.extension.oid4vci.nonce.CredentialNonceRepository;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfigurationQueryRepository;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

/** Issues a {@code c_nonce} (OpenID4VCI 1.0 Section 7). */
public class CredentialNonceHandler {

  AuthorizationServerConfigurationQueryRepository authorizationServerConfigurationQueryRepository;
  CredentialNonceRepository credentialNonceRepository;
  CredentialNonceIssuer credentialNonceIssuer = new CredentialNonceIssuer();

  public CredentialNonceHandler(
      AuthorizationServerConfigurationQueryRepository
          authorizationServerConfigurationQueryRepository,
      CredentialNonceRepository credentialNonceRepository) {
    this.authorizationServerConfigurationQueryRepository =
        authorizationServerConfigurationQueryRepository;
    this.credentialNonceRepository = credentialNonceRepository;
  }

  public CredentialNonceResponse handle(Tenant tenant) {
    AuthorizationServerConfiguration authorizationServerConfiguration =
        authorizationServerConfigurationQueryRepository.get(tenant);

    if (!authorizationServerConfiguration.hasCredentialIssuerMetadata()) {
      return CredentialNonceResponse.notFound();
    }

    CredentialNonce nonce =
        credentialNonceIssuer.issue(authorizationServerConfiguration.credentialNonceDuration());
    credentialNonceRepository.register(tenant, nonce);

    return CredentialNonceResponse.ok(nonce);
  }
}
