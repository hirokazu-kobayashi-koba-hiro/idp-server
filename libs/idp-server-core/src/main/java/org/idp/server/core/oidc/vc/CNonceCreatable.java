package org.idp.server.core.oidc.vc;

import org.idp.server.basic.random.RandomStringGenerator;
import org.idp.server.basic.type.verifiablecredential.CNonce;

public interface CNonceCreatable {
  RandomStringGenerator randomStringGenerator = new RandomStringGenerator(20);

  default CNonce createCNonce() {
    return new CNonce(randomStringGenerator.generate());
  }
}
