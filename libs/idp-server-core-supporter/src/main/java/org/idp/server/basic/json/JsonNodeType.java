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
