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
import java.util.stream.Collectors;
import org.idp.server.basic.json.JsonNodeWrapper;
import org.idp.server.basic.json.schema.format.JsonPropertyFormat;

public class JsonSchemaProperty {
  JsonNodeWrapper propertySchema;

  public JsonSchemaProperty(JsonNodeWrapper propertySchema) {
    this.propertySchema = propertySchema;
  }

  public static JsonSchemaProperty from(JsonNodeWrapper wrapper) {
    return new JsonSchemaProperty(wrapper);
  }

  public String key() {
    return "";
  }

  public String type() {
    if (!propertySchema.exists()) {
      return "";
    }
    return propertySchema.getValueOrEmptyAsString("type");
  }

  public int minLength() {
    return propertySchema.getValueAsInt("minLength");
  }

  public int maxLength() {
    return propertySchema.getValueAsInt("maxLength");
  }

  public String pattern() {
    return propertySchema.getValueOrEmptyAsString("pattern");
  }

  public boolean hasMinLength() {
    return propertySchema.contains("minLength");
  }

  public boolean hasMaxLength() {
    return propertySchema.contains("maxLength");
  }

  public boolean hasPattern() {
    return propertySchema.contains("pattern");
  }

  public boolean hasEnum() {
    return propertySchema.contains("enum");
  }

  public List<String> enumValues() {
    if (!hasEnum()) {
      return List.of();
    }
    List<JsonNodeWrapper> anEnumNode = propertySchema.getValueAsJsonNodeList("enum");
    return anEnumNode.stream().map(JsonNodeWrapper::asText).collect(Collectors.toList());
  }

  public String enumValuesAsString() {
    return String.join(",", enumValues());
  }

  public boolean hasFormat() {
    return propertySchema.contains("format");
  }

  public String formatAsString() {
    if (!hasFormat()) {
      return "undefined";
    }
    return propertySchema.getValueOrEmptyAsString("format");
  }

  public JsonPropertyFormat format() {
    return JsonPropertyFormat.of(formatAsString());
  }

  public boolean hasMinItems() {
    return propertySchema.contains("min_items");
  }

  public int minItems() {
    return propertySchema.getValueAsInt("min_items");
  }

  public boolean hasMaxItems() {
    return propertySchema.contains("max_items");
  }

  public int maxItems() {
    return propertySchema.getValueAsInt("max_items");
  }

  public boolean uniqueItems() {
    return propertySchema.contains("unique_items");
  }

  public boolean isStringType() {
    return "string".equals(type());
  }

  public boolean isArrayType() {
    return "array".equals(type());
  }

  public boolean isObjectType() {
    return "object".equals(type());
  }

  public JsonSchemaProperty objectSchema(String propertyName) {
    if (!isObjectType()) {
      return new JsonSchemaProperty(JsonNodeWrapper.empty());
    }
    return new JsonSchemaProperty(propertySchema.getValueAsJsonNode(propertyName));
  }

  public JsonSchemaProperty itemsSchema() {
    if (!isArrayType()) {
      return new JsonSchemaProperty(JsonNodeWrapper.empty());
    }

    return new JsonSchemaProperty(propertySchema.getValueAsJsonNode("items"));
  }

  public boolean exists() {
    return propertySchema != null && propertySchema.exists();
  }

  public boolean shouldStore() {
    return propertySchema.optValueAsBoolean("store", false);
  }

  public boolean shouldRespond() {
    return propertySchema.optValueAsBoolean("respond", true);
  }
}
