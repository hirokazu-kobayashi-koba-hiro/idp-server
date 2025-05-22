/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.extension.identity.verification.result;

public enum IdentityVerificationSource {
  APPLICATION("application"),
  MANUAL("manual"),
  IMPORT("import");

  String value;

  IdentityVerificationSource(String value) {
    this.value = value;
  }

  public static IdentityVerificationSource of(String value) {
    for (IdentityVerificationSource source : IdentityVerificationSource.values()) {
      if (source.value.equals(value)) {
        return source;
      }
    }
    throw new UnsupportedOperationException("Unsupported IdentityVerificationSource: " + value);
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
