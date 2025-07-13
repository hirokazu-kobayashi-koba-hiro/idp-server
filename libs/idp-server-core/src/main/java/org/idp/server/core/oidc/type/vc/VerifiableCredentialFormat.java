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

package org.idp.server.core.oidc.type.vc;

public enum VerifiableCredentialFormat {
  json("json"),
  json_ld("json-ld"),
  ldp("ldp");
  String value;

  VerifiableCredentialFormat(String value) {
    this.value = value;
  }

  public static VerifiableCredentialFormat of(String value) {
    for (VerifiableCredentialFormat format : VerifiableCredentialFormat.values()) {
      if (format.value.equals(value)) {
        return format;
      }
    }
    throw new VerifiableCredentialFormatInvalidException(
        String.format("invalid verifiable credential format (%s)", value));
  }

  public String value() {
    return value;
  }
}
