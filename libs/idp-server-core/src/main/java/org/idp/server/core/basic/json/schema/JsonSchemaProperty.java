package org.idp.server.core.basic.json.schema;

import org.idp.server.core.basic.json.JsonNodeWrapper;

public class JsonSchemaProperty {
  JsonNodeWrapper propertySchema;

  public JsonSchemaProperty(JsonNodeWrapper propertySchema) {
    this.propertySchema = propertySchema;
  }

  public String key() {
    return "";
  }

  public String type() {
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
