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
import org.idp.server.control_plane.management.exception.ResourceNotFoundException;
import org.idp.server.control_plane.management.federation.FederationConfigManagementContextBuilder;
import org.idp.server.control_plane.management.federation.io.FederationConfigManagementResponse;
import org.idp.server.control_plane.management.federation.io.FederationConfigManagementStatus;
import org.idp.server.control_plane.management.federation.io.FederationConfigRequest;
import org.idp.server.control_plane.management.federation.io.FederationConfigUpdateRequest;
import org.idp.server.core.openid.federation.FederationConfiguration;
import org.idp.server.core.openid.federation.repository.FederationConfigurationCommandRepository;
import org.idp.server.core.openid.federation.repository.FederationConfigurationQueryRepository;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.json.JsonDiffCalculator;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Service for federation configuration update operations.
 *
 * <p>Handles business logic for updating federation configurations. Part of Handler/Service
 * pattern.
 */
public class FederationConfigUpdateService
    implements FederationConfigManagementService<FederationConfigUpdateRequest> {

  private final FederationConfigurationQueryRepository queryRepository;
  private final FederationConfigurationCommandRepository commandRepository;

  public FederationConfigUpdateService(
      FederationConfigurationQueryRepository queryRepository,
      FederationConfigurationCommandRepository commandRepository) {
    this.queryRepository = queryRepository;
    this.commandRepository = commandRepository;
  }

  @Override
  public FederationConfigManagementResponse execute(
      FederationConfigManagementContextBuilder builder,
      Tenant tenant,
      User operator,
      OAuthToken oAuthToken,
      FederationConfigUpdateRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    // 1. Retrieve existing configuration
    FederationConfiguration before =
        queryRepository.findWithDisabled(tenant, request.identifier(), true);

    if (!before.exists()) {
      throw new ResourceNotFoundException(
          "Federation configuration not found: " + request.identifier().value());
    }

    // 2. Create updated configuration
    FederationConfiguration after = updateConfiguration(before, request.request());

    // 3. Populate builder with before/after
    builder.withBefore(before);
    builder.withAfter(after);

    // 4. Build response with diff
    JsonNodeWrapper beforeJson = JsonNodeWrapper.fromMap(before.toMap());
    JsonNodeWrapper afterJson = JsonNodeWrapper.fromMap(after.toMap());
    Map<String, Object> contents = new HashMap<>();
    contents.put("result", after.toMap());
    contents.put("diff", JsonDiffCalculator.deepDiff(beforeJson, afterJson));
    contents.put("dry_run", dryRun);

    if (dryRun) {
      return new FederationConfigManagementResponse(FederationConfigManagementStatus.OK, contents);
    }

    // 5. Repository operation
    commandRepository.update(tenant, after);

    return new FederationConfigManagementResponse(FederationConfigManagementStatus.OK, contents);
  }

  private FederationConfiguration updateConfiguration(
      FederationConfiguration before, FederationConfigRequest request) {
    JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();
    JsonNodeWrapper configJson = jsonConverter.readTree(request.toMap());

    String id = before.identifier().value();
    String type = configJson.getValueOrEmptyAsString("type");
    String ssoProvider = configJson.getValueOrEmptyAsString("sso_provider");
    JsonNodeWrapper payloadJson = configJson.getValueAsJsonNode("payload");
    Map<String, Object> payload = payloadJson.toMap();

    return new FederationConfiguration(id, type, ssoProvider, payload);
  }
}
