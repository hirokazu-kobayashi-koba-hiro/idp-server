/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.control_plane.management.security.hook;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.idp.server.basic.json.JsonConverter;
import org.idp.server.basic.json.JsonNodeWrapper;
import org.idp.server.control_plane.management.security.hook.io.SecurityEventHookConfigRequest;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.security.hook.SecurityEventHookConfiguration;

public class SecurityEventHookConfigRegistrationContextCreator {

  Tenant tenant;
  SecurityEventHookConfigRequest request;
  boolean dryRun;
  JsonConverter jsonConverter;

  public SecurityEventHookConfigRegistrationContextCreator(
      Tenant tenant, SecurityEventHookConfigRequest request, boolean dryRun) {
    this.tenant = tenant;
    this.request = request;
    this.dryRun = dryRun;
    this.jsonConverter = JsonConverter.snakeCaseInstance();
  }

  public SecurityEventHookConfigRegistrationContext create() {
    JsonNodeWrapper configJson = jsonConverter.readTree(request.toMap());
    String id =
        configJson.contains("id")
            ? configJson.getValueOrEmptyAsString("id")
            : UUID.randomUUID().toString();
    String type = configJson.getValueOrEmptyAsString("type");
    List<JsonNodeWrapper> triggersJson = configJson.getValueAsJsonNodeList("triggers");
    List<String> triggers = triggersJson.stream().map(JsonNodeWrapper::asText).toList();
    int executionOrder = configJson.getValueAsInt("execution_order");
    boolean enabled = configJson.optValueAsBoolean("enabled", false);
    JsonNodeWrapper payloadJson = configJson.getValueAsJsonNode("payload");
    Map<String, Object> payload = payloadJson.toMap();

    SecurityEventHookConfiguration configuration =
        new SecurityEventHookConfiguration(id, type, triggers, executionOrder, enabled, payload);

    return new SecurityEventHookConfigRegistrationContext(tenant, configuration, dryRun);
  }
}
