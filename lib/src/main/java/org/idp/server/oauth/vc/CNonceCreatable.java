package org.idp.server.oauth.vc;

import org.idp.server.basic.random.RandomStringGenerator;
import org.idp.server.type.verifiablecredential.CNonce;

public interface CNonceCreatable {
  RandomStringGenerator randomStringGenerator = new RandomStringGenerator(20);

  default CNonce createCNonce() {
    return new CNonce(randomStringGenerator.generate());
  }
}
