package org.idp.server.basic.uuid;

import java.util.UUID;

public class UuidMatcher {

  public static boolean isValid(String value) {
    if (value == null) return false;
    try {
      UUID.fromString(value);
      return true;
    } catch (IllegalArgumentException ignore) {
      return false;
    }
  }
}
