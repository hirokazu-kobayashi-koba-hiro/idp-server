package org.idp.server.core.oauth.vc;

import org.idp.server.core.basic.random.RandomStringGenerator;
import org.idp.server.core.type.verifiablecredential.CNonce;

public interface CNonceCreatable {
  RandomStringGenerator randomStringGenerator = new RandomStringGenerator(20);

  default CNonce createCNonce() {
    return new CNonce(randomStringGenerator.generate());
  }
}
