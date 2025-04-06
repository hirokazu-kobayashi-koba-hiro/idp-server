package org.idp.server.authenticators.webauthn4j.datasource.credential;

import java.util.Map;
import org.idp.server.authenticators.webauthn4j.WebAuthn4jCredential;

class ModelConverter {

  static WebAuthn4jCredential convert(Map<String, Object> result) {

    byte[] id = (byte[]) result.get("id");
    String userId = (String) result.get("user_id");
    String rpId = (String) result.get("rp_id");
    byte[] publicKey = (byte[]) result.get("public_key");
    byte[] attestationObject = (byte[]) result.get("attestation_object");
    long signCount = (long) result.get("sign_count");

    return new WebAuthn4jCredential(id, userId, rpId, publicKey, attestationObject, signCount);
  }
}
