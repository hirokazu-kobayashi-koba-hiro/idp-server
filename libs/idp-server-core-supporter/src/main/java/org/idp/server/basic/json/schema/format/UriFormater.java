/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.basic.json.schema.format;

import java.net.URI;
import java.net.URISyntaxException;

public class UriFormater implements JsonPropertyFormater {

  @Override
  public boolean match(String value) {
    try {
      new URI(value);
      return true;
    } catch (URISyntaxException e) {
      return false;
    }
  }
}
