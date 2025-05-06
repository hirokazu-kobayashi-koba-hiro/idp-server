package org.idp.server.core.identity.verification.application;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.basic.json.JsonNodeWrapper;
import org.idp.server.basic.json.schema.JsonSchemaDefinition;
import org.idp.server.core.identity.verification.IdentityVerificationRequest;
import org.idp.server.core.identity.verification.configuration.IdentityVerificationProcessConfiguration;

public class IdentityVerificationExaminationResult {
  JsonNodeWrapper json;

  public IdentityVerificationExaminationResult() {
    this.json = JsonNodeWrapper.empty();
  }

  public IdentityVerificationExaminationResult(JsonNodeWrapper json) {
    this.json = json;
  }

  public static IdentityVerificationExaminationResult create(IdentityVerificationRequest request, IdentityVerificationProcessConfiguration processConfig) {

    JsonSchemaDefinition jsonSchemaDefinition = processConfig.requestValidationSchemaAsDefinition();
    Map<String, Object> mappingResult = IdentityVerificationMapper.mapping(request.toMap(), jsonSchemaDefinition);

    return new IdentityVerificationExaminationResult(JsonNodeWrapper.fromObject(mappingResult));
  }

  public IdentityVerificationExaminationResult merge(IdentityVerificationRequest request, IdentityVerificationProcessConfiguration processConfig) {
    JsonSchemaDefinition jsonSchemaDefinition = processConfig.requestValidationSchemaAsDefinition();
    Map<String, Object> mappingResult = IdentityVerificationMapper.mapping(request.toMap(), jsonSchemaDefinition);
    Map<String, Object> mergedResult = new HashMap<>(json.toMap());;
    mergedResult.putAll(mappingResult);

    return new IdentityVerificationExaminationResult(JsonNodeWrapper.fromObject(mergedResult));
  }

  public String getValueOrEmptyAsString(String fieldName) {
    return json.getValueOrEmptyAsString(fieldName);
  }

  public Map<String, Object> toMap() {
    return json.toMap();
  }
}
