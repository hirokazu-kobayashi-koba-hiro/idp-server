package org.idp.server.basic.type.verifiablecredential;

import java.util.Objects;

public enum Format {
  jwt_vc_json("jwt_vc_json"),
  jwt_vc_json_ld("jwt_vc_json-ld"),
  ldp_vc("ldp_vc"),
  ldp_vp("ldp_vp"),
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

  public String value() {
    return value;
  }

  public boolean isDefined() {
    return this != undefined;
  }

  public boolean isJwtVc() {
    return this == jwt_vc_json || this == jwt_vc_json_ld;
  }

  public boolean isLdpVc() {
    return this == ldp_vc;
  }
}
