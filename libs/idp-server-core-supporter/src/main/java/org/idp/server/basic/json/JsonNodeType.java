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

package org.idp.server.basic.json;

public enum JsonNodeType {
  STRING("string"),
  INT("integer"),
  LONG("long"),
  DOUBLE("double"),
  BOOLEAN("boolean"),
  ARRAY("array"),
  OBJECT("object"),
  BINARY("binary"),
  DATE("date"),
  DATETIME("datetime"),
  TIME("time"),
  TIMESTAMP("timestamp"),
  NULL("null");

  String typeName;

  JsonNodeType(String typeName) {
    this.typeName = typeName;
  }

  public static JsonNodeType of(String type) {
    for (JsonNodeType value : values()) {
      if (value.typeName.equals(type)) {
        return value;
      }
    }
    return NULL;
  }

  public String typeName() {
    return typeName;
  }
}
