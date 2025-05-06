package org.idp.server.core.identity.verification.result;

public enum IdentityVerificationSource {
  APPLICATION("application"), MANUAL("manual"), IMPORT("import");

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
