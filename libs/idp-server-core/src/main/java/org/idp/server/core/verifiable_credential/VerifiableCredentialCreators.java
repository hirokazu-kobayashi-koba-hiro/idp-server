package org.idp.server.core.verifiable_credential;

import java.util.Map;
import java.util.Objects;
import org.idp.server.basic.type.verifiablecredential.Format;

public class VerifiableCredentialCreators {

  Map<Format, VerifiableCredentialCreator> values;

  public VerifiableCredentialCreators(Map<Format, VerifiableCredentialCreator> values) {
    this.values = values;
  }

  public VerifiableCredentialCreator get(Format format) {
    VerifiableCredentialCreator verifiableCredentialCreator = values.get(format);
    if (Objects.isNull(verifiableCredentialCreator)) {
      throw new RuntimeException(String.format("unsupported format (%s)", format.name()));
    }
    return verifiableCredentialCreator;
  }
}
