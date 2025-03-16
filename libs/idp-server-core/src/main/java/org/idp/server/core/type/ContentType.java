package org.idp.server.core.type;

public enum ContentType {
  application_json("application/json"),
  application_token_introspection_jwt("application/token-introspection+jwt");

  String value;

  ContentType(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }
}
