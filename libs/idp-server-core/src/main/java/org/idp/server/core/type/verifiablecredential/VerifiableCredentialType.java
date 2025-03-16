package org.idp.server.core.type.verifiablecredential;

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
