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

package org.idp.server.control_plane.management.oidc.client;

import java.util.Map;
import org.idp.server.control_plane.base.ConfigRegistrationContext;
import org.idp.server.control_plane.management.oidc.client.io.ClientManagementResponse;
import org.idp.server.control_plane.management.oidc.client.io.ClientManagementStatus;
import org.idp.server.core.oidc.configuration.client.ClientConfiguration;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class ClientRegistrationContext implements ConfigRegistrationContext {
  Tenant tenant;
  ClientConfiguration clientConfiguration;
  boolean dryRun;

  public ClientRegistrationContext(
      Tenant tenant, ClientConfiguration clientConfiguration, boolean dryRun) {
    this.tenant = tenant;
    this.clientConfiguration = clientConfiguration;
    this.dryRun = dryRun;
  }

  public Tenant tenant() {
    return tenant;
  }

  public ClientConfiguration configuration() {
    return clientConfiguration;
  }

  @Override
  public String type() {
    return "client";
  }

  @Override
  public Map<String, Object> payload() {
    return clientConfiguration.toMap();
  }

  @Override
  public boolean isDryRun() {
    return dryRun;
  }

  public ClientManagementResponse toResponse() {
    Map<String, Object> contents = Map.of("result", clientConfiguration.toMap(), "dry_run", dryRun);
    return new ClientManagementResponse(ClientManagementStatus.CREATED, contents);
  }
}
