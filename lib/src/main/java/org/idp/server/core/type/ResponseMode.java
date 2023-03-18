package org.idp.server.core.type;

import java.util.Objects;

/** ResponseMode */
public enum ResponseMode {
  query("query"),
  fragment("fragment"),
  form_post("form_post"),
  query_jwt("query.jwt"),
  fragment_jwt("fragment.jwt"),
  form_post_jwt("form_post.jwt"),
  jwt("jwt"),
  undefined(""),
  unknown("");

  String value;

  ResponseMode(String value) {
    this.value = value;
  }

  public static ResponseMode of(String value) {
    if (Objects.isNull(value) || value.isEmpty()) {
      return undefined;
    }
    for (ResponseMode responseMode : ResponseMode.values()) {
      if (responseMode.value.equals(value)) {
        return responseMode;
      }
    }
    return unknown;
  }
}
