package org.idp.server.authenticators.handler.webauthn.datasource.configuration;

import java.util.Base64;
import java.util.Map;
import org.idp.server.authenticators.webauthn.WebAuthnConfiguration;

class ModelConverter {

  static WebAuthnConfiguration convert(Map<String, String> stringMap) {
    String rpId = stringMap.get("rp_id");
    String rpName = stringMap.get("rp_name");
    String origin = stringMap.get("origin");

    byte[] tokenBindingId = getTokenBindingId(stringMap);

    String attestationPreference = stringMap.get("attestation_preference");
    String authenticatorAttachment = stringMap.get("authenticator_attachment");

    boolean requireResidentKey = Boolean.parseBoolean(stringMap.get("require_resident_key"));
    boolean userVerificationRequired =
        Boolean.parseBoolean(stringMap.get("user_verification_required"));
    boolean userPresenceRequired = Boolean.parseBoolean(stringMap.get("user_presence_required"));

    return new WebAuthnConfiguration(
        rpId,
        rpName,
        origin,
        tokenBindingId,
        attestationPreference,
        authenticatorAttachment,
        requireResidentKey,
        userVerificationRequired,
        userPresenceRequired);
  }

  private static byte[] getTokenBindingId(Map<String, String> stringMap) {
    if (stringMap.containsKey("token_binding_id")
        && !stringMap.getOrDefault("token_binding_id", "").isEmpty()) {
      return Base64.getDecoder().decode(stringMap.get("token_binding_id"));
    }
    return null;
  }
}
