package org.idp.server.type.pkce;

import java.util.Objects;

public enum CodeChallengeMethod {
  plain,
  s256,
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
    return this == s256;
  }
}