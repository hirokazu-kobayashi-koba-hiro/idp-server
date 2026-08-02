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

import java.util.EnumMap;
import java.util.Map;
import org.idp.server.core.openid.clientinstance.ClientInstanceQueryRepository;
import org.idp.server.platform.exception.UnSupportedException;

/**
 * Selects the {@link ClientAttestationKeyResolver} for the trust source configured on the client.
 *
 * @see ClientAttestationTrustSource
 */
public class ClientAttestationKeyResolvers {

  Map<ClientAttestationTrustSource, ClientAttestationKeyResolver> resolvers;

  public ClientAttestationKeyResolvers(
      ClientInstanceQueryRepository clientInstanceQueryRepository) {
    this.resolvers = new EnumMap<>(ClientAttestationTrustSource.class);
    resolvers.put(
        ClientAttestationTrustSource.registered_instance_key,
        new RegisteredInstanceKeyResolver(clientInstanceQueryRepository));
    resolvers.put(
        ClientAttestationTrustSource.attester_jwks, new StaticJwksClientAttestationKeyResolver());
  }

  /**
   * Returns the resolver of the given trust source.
   *
   * <p>Callers reject {@link ClientAttestationTrustSource#undefined} beforehand, so a missing entry
   * here means a trust source was added to the enum without registering its resolver.
   *
   * @throws UnSupportedException when no resolver is registered for the trust source
   */
  public ClientAttestationKeyResolver get(ClientAttestationTrustSource trustSource) {
    ClientAttestationKeyResolver resolver = resolvers.get(trustSource);

    if (resolver == null) {
      throw new UnSupportedException(
          "no client attestation key resolver registered for trust source " + trustSource.name());
    }

    return resolver;
  }
}
