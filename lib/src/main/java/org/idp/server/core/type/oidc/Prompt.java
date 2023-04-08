package org.idp.server.core.type.oidc;

import java.util.Objects;

/** Prompt */
public enum Prompt {
  none,
  login,
  consent,
  select_account,
  undefined,
  unknown;

  public static Prompt of(String value) {
    if (Objects.isNull(value) || value.isEmpty()) {
      return undefined;
    }
    for (Prompt prompt : Prompt.values()) {
      if (prompt.name().equals(value)) {
        return prompt;
      }
    }
    return unknown;
  }
}
