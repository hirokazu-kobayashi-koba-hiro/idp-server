package org.idp.server.core.extension.identity.verification.configuration;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.json.JsonReadable;
import org.idp.server.platform.json.schema.JsonSchemaDefinition;

public class IdentityVerificationRequestConfiguration implements JsonReadable {

  Map<String, Object> validationSchema = new HashMap<>();

  public JsonSchemaDefinition requestValidationSchemaAsDefinition() {
    if (validationSchema == null) {
      return new JsonSchemaDefinition(JsonNodeWrapper.empty());
    }
    return new JsonSchemaDefinition(JsonNodeWrapper.fromMap(validationSchema));
  }
}
