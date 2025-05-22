/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.basic.type.extension;

/** ResponseModeValue ? or # */
public class ResponseModeValue {
  String value;

  public ResponseModeValue() {}

  public ResponseModeValue(String value) {
    this.value = value;
  }

  public static ResponseModeValue query() {
    return new ResponseModeValue("?");
  }

  public static ResponseModeValue fragment() {
    return new ResponseModeValue("#");
  }

  public String value() {
    return value;
  }
}
