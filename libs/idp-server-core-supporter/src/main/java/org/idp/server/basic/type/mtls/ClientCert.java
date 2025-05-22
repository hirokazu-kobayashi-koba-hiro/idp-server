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


package org.idp.server.basic.type.mtls;

import java.util.Objects;
import org.idp.server.basic.base64.Base64Codeable;

public class ClientCert implements Base64Codeable {
  String value;

  public ClientCert() {}

  public ClientCert(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  public String plainValue() {
    if (value.contains("-----BEGIN CERTIFICATE-----")) {
      return value.replaceAll("%0A", "\n");
    }
    return decodeWithUrlSafe(value);
  }

  public boolean exists() {
    return Objects.nonNull(value) && !value.isEmpty();
  }
}
