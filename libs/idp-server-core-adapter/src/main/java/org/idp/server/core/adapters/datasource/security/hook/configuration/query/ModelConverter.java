package org.idp.server.core.adapters.datasource.security.hook.configuration.query;

import java.util.List;
import java.util.Map;
import org.idp.server.basic.json.JsonNodeWrapper;
import org.idp.server.core.security.hook.SecurityEventHookConfiguration;

class ModelConverter {

  public static SecurityEventHookConfiguration convert(Map<String, String> result) {
    String id = result.get("id");
    String type = result.get("type");
    JsonNodeWrapper triggersNode = JsonNodeWrapper.fromString(result.get("triggers"));
    List<String> triggers = triggersNode.toList();
    JsonNodeWrapper payloadNode = JsonNodeWrapper.fromString(result.get("payload"));
    Map<String, Object> payload = payloadNode.toMap();
    int executionOrder = Integer.parseInt(result.get("execution_order"));
    boolean enabled = Boolean.parseBoolean(result.get("enabled"));

    return new SecurityEventHookConfiguration(id, type, triggers, executionOrder, enabled, payload);
  }
}
