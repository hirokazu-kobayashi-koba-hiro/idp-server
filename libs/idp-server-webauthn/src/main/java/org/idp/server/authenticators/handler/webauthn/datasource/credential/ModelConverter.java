package org.idp.server.authenticators.handler.webauthn.datasource.credential;

import org.idp.server.authenticators.webauthn.WebAuthnCredential;

import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;

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
