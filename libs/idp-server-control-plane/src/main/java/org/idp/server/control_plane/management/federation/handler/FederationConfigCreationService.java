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

package org.idp.server.control_plane.management.federation.handler;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.idp.server.control_plane.management.federation.FederationConfigManagementContextBuilder;
import org.idp.server.control_plane.management.federation.io.FederationConfigManagementResponse;
import org.idp.server.control_plane.management.federation.io.FederationConfigManagementStatus;
import org.idp.server.control_plane.management.federation.io.FederationConfigRequest;
import org.idp.server.core.openid.federation.FederationConfiguration;
import org.idp.server.core.openid.federation.repository.FederationConfigurationCommandRepository;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Service for federation configuration creation operations.
 *
 * <p>Handles business logic for creating federation configurations. Part of Handler/Service
 * pattern.
 */
public class FederationConfigCreationService
    implements FederationConfigManagementService<FederationConfigRequest> {

  private final FederationConfigurationCommandRepository commandRepository;

  public FederationConfigCreationService(
      FederationConfigurationCommandRepository commandRepository) {
    this.commandRepository = commandRepository;
  }

  @Override
  public FederationConfigManagementResponse execute(
      FederationConfigManagementContextBuilder builder,
      Tenant tenant,
      User operator,
      OAuthToken oAuthToken,
      FederationConfigRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    // 1. Create configuration from request
    FederationConfiguration configuration = createConfiguration(request);

    // 2. Populate builder with created configuration
    builder.withAfter(configuration);

    // 3. Build response
    Map<String, Object> contents = Map.of("result", configuration.toMap(), "dry_run", dryRun);

    if (dryRun) {
      return new FederationConfigManagementResponse(FederationConfigManagementStatus.OK, contents);
    }

    // 4. Repository operation
    commandRepository.register(tenant, configuration);

    return new FederationConfigManagementResponse(
        FederationConfigManagementStatus.CREATED, contents);
  }

  FederationConfiguration createConfiguration(FederationConfigRequest request) {
    // Full replacement, mirroring the update path / client management: deserialize the whole
    // configuration from the request (honoring enabled), generating an id when the body omits one.
    Map<String, Object> map = new HashMap<>(request.toMap());
    map.putIfAbsent("id", UUID.randomUUID().toString());
    return JsonConverter.snakeCaseInstance().read(map, FederationConfiguration.class);
  }
}
