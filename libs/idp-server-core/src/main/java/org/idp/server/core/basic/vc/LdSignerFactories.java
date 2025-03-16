package org.idp.server.core.basic.vc;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.idp.server.core.type.exception.UnSupportedException;

public class LdSignerFactories {
  Map<ProofType, LdSignerFactory> values;

  public LdSignerFactories() {
    this.values = new HashMap<>();
    values.put(ProofType.RsaSignature2018, new RsaSignature2018Factory());
  }

  public LdSignerFactory get(ProofType proofType) {
    LdSignerFactory ldSignerFactory = values.get(proofType);
    if (Objects.isNull(ldSignerFactory)) {
      throw new UnSupportedException(String.format("unsupported ProofType (%s)", proofType.name()));
    }
    return ldSignerFactory;
  }
}
