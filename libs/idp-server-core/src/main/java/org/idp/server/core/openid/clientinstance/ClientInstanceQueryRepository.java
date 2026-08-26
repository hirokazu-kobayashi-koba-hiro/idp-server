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

package org.idp.server.core.openid.clientinstance;

import java.util.List;
import org.idp.server.core.openid.oauth.type.oauth.RequestedClientId;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public interface ClientInstanceQueryRepository {

  /**
   * Finds a registered Client Instance by its identifier.
   *
   * <p>Called on every authentication with {@code client_attestation_trust_source =
   * registered_instance_key}, so revocation takes effect immediately.
   *
   * @return the instance, or a non-existing instance when not found
   */
  ClientInstance find(
      Tenant tenant, RequestedClientId requestedClientId, ClientInstanceIdentifier identifier);

  List<ClientInstance> findList(
      Tenant tenant, RequestedClientId requestedClientId, int limit, int offset);

  /**
   * Returns the active instances registered for a device.
   *
   * <p>Registration rejects a device that already holds one, so that a captured piece of platform
   * evidence cannot add a second key alongside the legitimate one.
   */
  List<ClientInstance> findActiveListByDevice(
      Tenant tenant, RequestedClientId requestedClientId, String deviceId);
}
