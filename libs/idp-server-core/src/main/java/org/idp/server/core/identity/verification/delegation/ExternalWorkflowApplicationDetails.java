package org.idp.server.core.identity.verification.delegation;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.basic.json.JsonNodeWrapper;
import org.idp.server.core.basic.json.schema.JsonSchemaDefinition;
import org.idp.server.core.identity.verification.application.IdentityVerificationApplicationMapper;
import org.idp.server.core.identity.verification.configuration.IdentityVerificationProcessConfiguration;

public class ExternalWorkflowApplicationDetails {

  JsonNodeWrapper json;

  public ExternalWorkflowApplicationDetails() {
    this.json = JsonNodeWrapper.empty();
  }

  public ExternalWorkflowApplicationDetails(JsonNodeWrapper json) {
    this.json = json;
  }

  public static ExternalWorkflowApplicationDetails create(
      JsonNodeWrapper body, IdentityVerificationProcessConfiguration processConfig) {
    JsonSchemaDefinition jsonSchemaDefinition =
        processConfig.responseValidationSchemaAsDefinition();
    Map<String, Object> mappingResult =
        IdentityVerificationApplicationMapper.mapping(body.toMap(), jsonSchemaDefinition);
    return new ExternalWorkflowApplicationDetails(JsonNodeWrapper.fromObject(mappingResult));
  }

  public ExternalWorkflowApplicationDetails merge(
      JsonNodeWrapper body, IdentityVerificationProcessConfiguration processConfig) {
    JsonSchemaDefinition jsonSchemaDefinition =
        processConfig.responseValidationSchemaAsDefinition();
    Map<String, Object> mappingResult =
        IdentityVerificationApplicationMapper.mapping(body.toMap(), jsonSchemaDefinition);
    Map<String, Object> merged = new HashMap<>(json.toMap());
    merged.putAll(mappingResult);
    return new ExternalWorkflowApplicationDetails(JsonNodeWrapper.fromObject(merged));
  }

  public String getValueOrEmptyAsString(String fieldName) {
    return json.getValueOrEmptyAsString(fieldName);
  }

  public Map<String, Object> toMap() {
    return json.toMap();
  }

  public boolean exists() {
    return json != null && json.exists();
  }
}
