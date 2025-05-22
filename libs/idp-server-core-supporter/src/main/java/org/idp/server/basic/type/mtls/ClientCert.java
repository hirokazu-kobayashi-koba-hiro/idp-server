/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
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
