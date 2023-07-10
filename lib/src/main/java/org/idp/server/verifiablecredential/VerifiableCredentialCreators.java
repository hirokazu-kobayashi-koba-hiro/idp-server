package org.idp.server.verifiablecredential;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.idp.server.type.verifiablecredential.Format;

public class VerifiableCredentialCreators {

  Map<Format, VerifiableCredentialCreator> values;

  public VerifiableCredentialCreators() {
    this.values = new HashMap<>();
    values.put(Format.jwt_vc_json, new JwtVcJsonVerifiableCredentialCreator());
  }

  public VerifiableCredentialCreator get(Format format) {
    VerifiableCredentialCreator verifiableCredentialCreator = values.get(format);
    if (Objects.isNull(verifiableCredentialCreator)) {
      throw new RuntimeException(String.format("unsupported format", format.name()));
    }
    return verifiableCredentialCreator;
  }
}
