package org.idp.server.basic.vc;

public enum VerifiableCredentialFormat {
  json("json"), json_ld("json-ld"), ldp("ldp");

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
    throw new VerifiableCredentialFormatInvalidException(String.format("invalid verifiable credential format (%s)", value));
  }

  public String value() {
    return value;
  }
}
