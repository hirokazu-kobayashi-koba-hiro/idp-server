/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.basic.type.verifiablecredential;

import java.util.List;

public enum VerifiableCredentialType {
  vc(List.of("VerifiableCredential")),
  vp(List.of("VerifiablePresentation"));

  List<String> types;

  VerifiableCredentialType(List<String> types) {
    this.types = types;
  }

  public List<String> types() {
    return types;
  }
}
