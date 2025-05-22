/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.basic.type.pkce;

import java.util.Objects;

public enum CodeChallengeMethod {
  plain,
  S256,
  unknown,
  undefined;

  public static CodeChallengeMethod of(String value) {
    if (Objects.isNull(value) || value.isEmpty()) {
      return undefined;
    }
    for (CodeChallengeMethod codeChallengeMethod : CodeChallengeMethod.values()) {
      if (codeChallengeMethod.name().equals(value)) {
        return codeChallengeMethod;
      }
    }
    return unknown;
  }

  public boolean isS256() {
    return this == S256;
  }

  public boolean isDefined() {
    return this != undefined;
  }
}
