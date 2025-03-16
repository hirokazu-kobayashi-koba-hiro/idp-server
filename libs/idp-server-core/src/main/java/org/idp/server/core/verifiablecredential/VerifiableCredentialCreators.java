package org.idp.server.core.verifiablecredential;

import java.util.Map;
import java.util.Objects;
import org.idp.server.core.type.verifiablecredential.Format;

public class VerifiableCredentialCreators {

  Map<Format, VerifiableCredentialCreator> values;

  public VerifiableCredentialCreators(Map<Format, VerifiableCredentialCreator> values) {
    this.values = values;
  }

  public VerifiableCredentialCreator get(Format format) {
    VerifiableCredentialCreator verifiableCredentialCreator = values.get(format);
    if (Objects.isNull(verifiableCredentialCreator)) {
      throw new RuntimeException(String.format("unsupported format", format.name()));
    }
    return verifiableCredentialCreator;
  }
}
