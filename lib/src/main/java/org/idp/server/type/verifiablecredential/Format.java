package org.idp.server.type.verifiablecredential;

import java.util.Objects;

public enum Format {
  jwt_vc_json("jwt_vc_json"),
  jwt_vc_json_ld("jwt_vc_json-ld"),
  unknown(""),
  undefined("");

  String value;

  Format(String value) {
    this.value = value;
  }

  public static Format of(String value) {
    if (Objects.isNull(value) || value.isEmpty()) {
      return undefined;
    }
    for (Format format : Format.values()) {
      if (format.value.equals(value)) {
        return format;
      }
    }
    return unknown;
  }
}
