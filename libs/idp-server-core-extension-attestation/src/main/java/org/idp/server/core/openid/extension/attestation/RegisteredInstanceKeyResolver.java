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

import org.idp.server.core.openid.clientinstance.ClientInstance;
import org.idp.server.core.openid.clientinstance.ClientInstanceIdentifier;
import org.idp.server.core.openid.clientinstance.ClientInstanceQueryRepository;
import org.idp.server.core.openid.oauth.clientauthenticator.BackchannelRequestContext;
import org.idp.server.platform.jose.JsonWebSignatureHeader;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

/**
 * Resolves the trusted key from the Client Instance registered at the Authorization Server
 * (self-signed model).
 *
 * <p>The {@code kid} JOSE header parameter of the Client Attestation JWT selects which registered
 * instance to look up. It is a lookup key only: authentication succeeds only when the signature
 * verifies with the registered key, so a forged {@code kid} merely selects a key that will not
 * verify.
 *
 * <p>Because the key is read on every request, revoking or expiring a Client Instance takes effect
 * immediately.
 */
public class RegisteredInstanceKeyResolver implements ClientAttestationKeyResolver {

  ClientInstanceQueryRepository clientInstanceQueryRepository;

  public RegisteredInstanceKeyResolver(
      ClientInstanceQueryRepository clientInstanceQueryRepository) {
    this.clientInstanceQueryRepository = clientInstanceQueryRepository;
  }

  @Override
  public String resolveJwks(BackchannelRequestContext context, JsonWebSignatureHeader header) {
    Tenant tenant = context.tenant();
    if (tenant == null || !header.hasKid()) {
      return null;
    }

    ClientInstance clientInstance =
        clientInstanceQueryRepository.find(
            tenant, context.requestedClientId(), new ClientInstanceIdentifier(header.kid()));

    if (!clientInstance.isActive()) {
      return null;
    }

    return clientInstance.instanceKeyAsJwks();
  }
}
