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

  /**
   * Finds an instance by its identifier alone, whatever its client. For the management API: an
   * operator does not necessarily know which client an instance belongs to.
   *
   * @return the instance, or a non-existing instance when not found
   */
  ClientInstance find(Tenant tenant, ClientInstanceIdentifier identifier);

  /** Instances of the tenant matching the conditions, newest first. */
  List<ClientInstance> findList(Tenant tenant, ClientInstanceQueries queries);

  long findTotalCount(Tenant tenant, ClientInstanceQueries queries);

  /**
   * Finds the instance holding a key, whatever its client and status.
   *
   * <p>A key belongs to at most one instance within a tenant, revoked ones included: registering it
   * again would give it a second way to authenticate, and a revoked key must stay revoked.
   *
   * @return the instance, or a non-existing instance when the key is not registered
   */
  ClientInstance findByThumbprint(Tenant tenant, ClientInstanceThumbprint thumbprint);

  /**
   * Returns the instances of a client bound to a user whose status is active, expired ones
   * included.
   *
   * <p>A user holds one active instance per client: registering a new one revokes the others. The
   * set is the one the database constrains to a single row, so an expired instance still counts.
   */
  List<ClientInstance> findActiveListByUser(
      Tenant tenant, RequestedClientId requestedClientId, String userId);

  /**
   * Returns every instance bound to a user, across clients and statuses. For deleting them with the
   * user.
   */
  List<ClientInstance> findListByUser(Tenant tenant, String userId);
}
