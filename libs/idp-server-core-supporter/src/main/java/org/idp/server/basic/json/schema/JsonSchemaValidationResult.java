/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.basic.json.schema;

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

  public static JsonSchemaValidationResult empty() {
    return new JsonSchemaValidationResult(true, List.of());
  }

  public boolean isValid() {
    return valid;
  }

  public List<String> errors() {
    return errors;
  }
}
