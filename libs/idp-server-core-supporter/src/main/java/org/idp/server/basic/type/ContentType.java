/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.basic.type;

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
