package org.idp.server.core.verifiablecredential;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.idp.server.core.handler.credential.client.VerifiableCredentialBlockCertClient;
import org.idp.server.core.handler.credential.client.VerifiableCredentialJwtClient;
import org.idp.server.core.type.verifiablecredential.Format;

public class VerifiableCredentialCreators {

  Map<Format, VerifiableCredentialCreator> values;

  public VerifiableCredentialCreators() {
    this.values = new HashMap<>();
    values.put(Format.jwt_vc_json, new VerifiableCredentialJwtClient());
    values.put(Format.ldp_vc, new VerifiableCredentialBlockCertClient());
  }

  public VerifiableCredentialCreator get(Format format) {
    VerifiableCredentialCreator verifiableCredentialCreator = values.get(format);
    if (Objects.isNull(verifiableCredentialCreator)) {
      throw new RuntimeException(String.format("unsupported format", format.name()));
    }
    return verifiableCredentialCreator;
  }
}
