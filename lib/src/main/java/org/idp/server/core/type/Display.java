package org.idp.server.core.type;

import java.util.Objects;

/** Display */
public enum Display {
  page,
  popup,
  touch,
  wap,
  undefined,
  unknown;

  public static Display of(String value) {
    if (Objects.isNull(value) || value.isEmpty()) {
      return undefined;
    }
    for (Display display : Display.values()) {
      if (display.name().equals(value)) {
        return display;
      }
    }
    return unknown;
  }
}
