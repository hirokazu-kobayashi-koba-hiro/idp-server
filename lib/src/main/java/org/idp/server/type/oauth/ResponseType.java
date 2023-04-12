package org.idp.server.type.oauth;

import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** ResponseType */
public enum ResponseType {
  code(Set.of("code")),
  token(Set.of("token")),
  id_token(Set.of("id_token")),
  code_token(Set.of("code", "token")),
  code_token_id_token(Set.of("code", "token", "id_token")),
  code_id_token(Set.of("code", "id_token")),
  token_id_token(Set.of("token", "id_token")),
  undefined(Set.of()),
  unknown(Set.of());

  Set<String> values;

  ResponseType(Set<String> values) {
    this.values = values;
  }

  public static ResponseType of(String input) {
    if (Objects.isNull(input) || input.isEmpty()) {
      return undefined;
    }
    Set<String> inputValues = Arrays.stream(input.split(" ")).collect(Collectors.toSet());
    for (ResponseType responseType : ResponseType.values()) {
      if (responseType.values.containsAll(inputValues)) {
        return responseType;
      }
    }
    return unknown;
  }

  public boolean isAuthorizationCodeFlow() {
    return this == code;
  }

  public boolean isImplicitFlow() {
    return this == token || this == id_token || this == token_id_token;
  }

  public boolean isHybridFlow() {
    return this == code_token || this == code_id_token || this == code_token_id_token;
  }

  public boolean isUndefined() {
    return this == undefined;
  }

  public boolean isUnknown() {
    return this == unknown;
  }
}
