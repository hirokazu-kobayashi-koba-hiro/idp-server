package org.idp.server.authenticators.handler.webauthn.datasource.credential;

import org.idp.server.authenticators.webauthn.WebAuthnCredential;

import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;

class ModelConverter {

  static WebAuthnCredential convert(Map<String, String> stringMap) {

    byte[] id = HexFormat.of().parseHex(stringMap.get("id"));
    String userId = stringMap.get("user_id");
    String rpId = stringMap.get("rp_id");
    byte[] publicKey = Base64.getDecoder().decode(stringMap.get("public_key"));
    byte[] attestationObject = Base64.getDecoder().decode(stringMap.get("attestation_object"));
    long signCount = Long.parseLong(stringMap.get("sign_count"));

    return new WebAuthnCredential(id, userId, rpId, publicKey, attestationObject, signCount);
  }


}
