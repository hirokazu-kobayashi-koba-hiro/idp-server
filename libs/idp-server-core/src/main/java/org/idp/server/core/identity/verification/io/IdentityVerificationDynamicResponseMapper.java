package org.idp.server.core.identity.verification.io;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.basic.json.JsonNodeWrapper;
import org.idp.server.core.basic.json.schema.JsonSchemaDefinition;
import org.idp.server.core.basic.json.schema.JsonSchemaProperty;
import org.idp.server.core.identity.verification.IdentityVerificationProcess;
import org.idp.server.core.identity.verification.application.IdentityVerificationApplication;
import org.idp.server.core.identity.verification.configuration.IdentityVerificationConfiguration;
import org.idp.server.core.identity.verification.configuration.IdentityVerificationProcessConfiguration;

public class IdentityVerificationDynamicResponseMapper {

  public static Map<String, Object> buildDynamicResponse(
      IdentityVerificationApplication application,
      JsonNodeWrapper externalResponse,
      IdentityVerificationProcess process,
      IdentityVerificationConfiguration verificationConfiguration) {

    Map<String, Object> userResponse = new HashMap<>();
    userResponse.put("id", application.identifier().value());
    userResponse.put(
        "external_workflow_application_id", application.externalApplicationId().value());
    userResponse.put("status", application.status().value());

    IdentityVerificationProcessConfiguration processConfig =
        verificationConfiguration.getProcessConfig(process);
    JsonSchemaDefinition responseSchemaDefinition =
        processConfig.responseValidationSchemaAsDefinition();
    Map<String, JsonSchemaProperty> properties = responseSchemaDefinition.getProperties();

    for (Map.Entry<String, JsonSchemaProperty> entry : properties.entrySet()) {
      String field = entry.getKey();
      JsonSchemaProperty property = entry.getValue();

      if (property.shouldRespond() && externalResponse.contains(field)) {
        userResponse.put(field, externalResponse.getValue(field));
      }
    }
    return userResponse;
  }
}
