package org.idp.server.basic.json.schema.format;

import java.util.UUID;

public class UuidFormater implements JsonPropertyFormater {

  @Override
  public boolean match(String value) {
    try {
      UUID.fromString(value);
      return true;
    } catch (IllegalArgumentException e) {
      return false;
    }
  }
}
