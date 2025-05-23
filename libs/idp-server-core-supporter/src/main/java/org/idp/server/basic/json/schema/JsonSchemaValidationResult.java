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
