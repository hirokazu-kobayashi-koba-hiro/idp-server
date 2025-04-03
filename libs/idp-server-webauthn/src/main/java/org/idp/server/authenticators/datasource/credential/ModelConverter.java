package org.idp.server.authenticators.datasource.credential;

import java.util.Map;
import org.idp.server.authenticators.webauthn.WebAuthnCredential;

class ModelConverter {

  static WebAuthnCredential convert(Map<String, Object> result) {

    byte[] id = (byte[]) result.get("id");
    String userId = (String) result.get("user_id");
    String rpId = (String) result.get("rp_id");
    byte[] publicKey = (byte[]) result.get("public_key");
    byte[] attestationObject = (byte[]) result.get("attestation_object");
    long signCount = (long) result.get("sign_count");

    return new WebAuthnCredential(id, userId, rpId, publicKey, attestationObject, signCount);
  }
}
