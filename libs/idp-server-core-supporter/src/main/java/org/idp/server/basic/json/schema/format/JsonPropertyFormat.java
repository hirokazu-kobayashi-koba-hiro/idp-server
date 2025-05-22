/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.basic.json.schema.format;

public enum JsonPropertyFormat {
  UUID(new UuidFormater()),
  URI(new UriFormater()),
  UNDEFINED(new NoActionFormater());

  JsonPropertyFormater formater;

  JsonPropertyFormat(JsonPropertyFormater formater) {
    this.formater = formater;
  }

  public static JsonPropertyFormat of(String name) {
    for (JsonPropertyFormat format : JsonPropertyFormat.values()) {
      if (format.name().equalsIgnoreCase(name)) {
        return format;
      }
    }
    return UNDEFINED;
  }

  public boolean match(String target) {
    return formater.match(target);
  }
}
