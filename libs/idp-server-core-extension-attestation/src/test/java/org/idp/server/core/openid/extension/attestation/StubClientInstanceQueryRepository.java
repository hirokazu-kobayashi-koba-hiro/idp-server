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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.core.openid.clientinstance.ClientInstance;
import org.idp.server.core.openid.clientinstance.ClientInstanceIdentifier;
import org.idp.server.core.openid.clientinstance.ClientInstanceQueryRepository;
import org.idp.server.core.openid.oauth.type.oauth.RequestedClientId;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

/** In-memory {@link ClientInstanceQueryRepository} keyed by instance identifier. */
class StubClientInstanceQueryRepository implements ClientInstanceQueryRepository {

  Map<String, ClientInstance> instances = new HashMap<>();

  void put(ClientInstance clientInstance) {
    instances.put(clientInstance.id(), clientInstance);
  }

  @Override
  public ClientInstance find(
      Tenant tenant, RequestedClientId requestedClientId, ClientInstanceIdentifier identifier) {
    ClientInstance clientInstance = instances.get(identifier.value());
    return clientInstance != null ? clientInstance : new ClientInstance();
  }

  @Override
  public List<ClientInstance> findList(
      Tenant tenant, RequestedClientId requestedClientId, int limit, int offset) {
    return List.copyOf(instances.values());
  }
}
