package org.idp.server.basic.jose;

import com.nimbusds.jose.JWSVerifier;

/** JsonWebSignatureVerifier */
public class JsonWebSignatureVerifier {

  JWSVerifier verifier;

  public JsonWebSignatureVerifier() {}

  public JsonWebSignatureVerifier(JWSVerifier verifier) {
    this.verifier = verifier;
  }

  public void verify(JsonWebSignature jsonWebSignature) throws JoseInvalidException {
    boolean verified = jsonWebSignature.verify(verifier);
    if (!verified) {
      throw new JoseInvalidException("invalid signature");
    }
  }
}
