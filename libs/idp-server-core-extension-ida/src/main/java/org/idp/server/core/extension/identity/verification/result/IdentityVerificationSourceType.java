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

package org.idp.server.core.extension.identity.verification.result;

public enum IdentityVerificationSourceType {
  APPLICATION("application"),
  DIRECT("direct"),
  MANUAL("manual"),
  IMPORT("import"),
  UNKNOWN("");

  String value;

  IdentityVerificationSourceType(String value) {
    this.value = value;
  }

  public static IdentityVerificationSourceType of(String value) {
    for (IdentityVerificationSourceType source : IdentityVerificationSourceType.values()) {
      if (source.value.equalsIgnoreCase(value)) {
        return source;
      }
    }
    return UNKNOWN;
  }

  public String value() {
    return value;
  }

  public boolean isManual() {
    return this == MANUAL;
  }

  public boolean isImport() {
    return this == IMPORT;
  }
}
