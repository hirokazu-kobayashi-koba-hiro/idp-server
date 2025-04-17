package org.idp.server.core.basic.json.schema;

import java.util.List;

public class JsonSchemaValidationResult {

  boolean valid;
  List<String> errors;

  private JsonSchemaValidationResult(boolean valid, List<String> errors) {
    this.valid = valid;
    this.errors = errors;
  }

  public static JsonSchemaValidationResult success() {
    return new JsonSchemaValidationResult(true, List.of());
  }

  public static JsonSchemaValidationResult failure(List<String> errors) {
    return new JsonSchemaValidationResult(false, errors);
  }

  public boolean isValid() {
    return valid;
  }

  public List<String> errors() {
    return errors;
  }
}
