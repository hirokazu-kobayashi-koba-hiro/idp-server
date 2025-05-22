/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.basic.json;

public class JsonConvertable {
  static JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();

  public static <TYPE> TYPE read(String value, Class<TYPE> typeClass) {
    return jsonConverter.read(value, typeClass);
  }

  public static String write(Object value) {
    return jsonConverter.write(value);
  }
}
