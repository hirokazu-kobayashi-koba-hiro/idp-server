/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.extension.verifiable_credentials;

import java.util.Objects;

public class VerifiableCredential {
  Object value;

  public VerifiableCredential() {}

  public VerifiableCredential(Object value) {
    this.value = value;
  }

  public Object value() {
    return value;
  }

  public boolean exists() {
    return Objects.nonNull(value);
  }
}
