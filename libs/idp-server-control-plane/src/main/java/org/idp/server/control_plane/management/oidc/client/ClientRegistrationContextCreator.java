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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.idp.server.control_plane.management.oidc.client.io.ClientRegistrationRequest;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfiguration;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class ClientRegistrationContextCreator {

  Tenant tenant;
  ClientRegistrationRequest request;
  boolean dryRun;
  JsonConverter jsonConverter;

  public ClientRegistrationContextCreator(
      Tenant tenant, ClientRegistrationRequest request, boolean dryRun) {
    this.tenant = tenant;
    this.request = request;
    this.dryRun = dryRun;
    this.jsonConverter = JsonConverter.snakeCaseInstance();
  }

  public ClientRegistrationContext create() {
    Map<String, Object> map = new HashMap<>(request.toMap());
    if (!request.hasClientId()) {
      map.put("client_id", UUID.randomUUID().toString());
    }
    ClientConfiguration clientConfiguration = jsonConverter.read(map, ClientConfiguration.class);

    return new ClientRegistrationContext(tenant, clientConfiguration, dryRun);
  }
}
