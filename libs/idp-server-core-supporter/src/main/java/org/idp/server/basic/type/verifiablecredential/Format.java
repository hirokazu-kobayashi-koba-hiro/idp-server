/*
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


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
