/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.basic.type.verifiablecredential;

import java.util.Objects;

public class CredentialDefinitionEntity {
  Object value;

  public CredentialDefinitionEntity() {}

  public CredentialDefinitionEntity(Object value) {
    this.value = value;
  }

  public Object value() {
    return value;
  }

  public boolean exists() {
    return Objects.nonNull(value);
  }
}
