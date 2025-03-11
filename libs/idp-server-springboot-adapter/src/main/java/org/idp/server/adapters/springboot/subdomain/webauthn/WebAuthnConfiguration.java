package org.idp.server.adapters.springboot.subdomain.webauthn;

import com.webauthn4j.data.PublicKeyCredentialParameters;
import com.webauthn4j.data.RegistrationParameters;
import com.webauthn4j.data.client.Origin;
import com.webauthn4j.server.ServerProperty;
import java.util.List;

public class WebAuthnConfiguration {
  String rpId;
  String rpName;
  String origin;
  byte[] tokenBindingId;
  String attestationPreference;
  String authenticatorAttachment;

  boolean requireResidentKey;
  boolean userVerificationRequired;
  boolean userPresenceRequired;

  // FIXME
  RegistrationParameters toRegistrationParameters(WebAuthnSession session) {

    // Server properties
    Origin origin = Origin.create(this.origin);
    ServerProperty serverProperty =
        new ServerProperty(origin, rpId, session.challenge(), tokenBindingId);

    // expectations
    List<PublicKeyCredentialParameters> pubKeyCredParams = null;

    return new RegistrationParameters(
        serverProperty, pubKeyCredParams, userVerificationRequired, userPresenceRequired);
  }

  public String rpId() {
    return rpId;
  }

  public String rpName() {
    return rpName;
  }

  public String origin() {
    return origin;
  }

  public byte[] tokenBindingId() {
    return tokenBindingId;
  }

  public String attestationPreference() {
    return attestationPreference;
  }

  public String authenticatorAttachment() {
    return authenticatorAttachment;
  }

  public boolean requireResidentKey() {
    return requireResidentKey;
  }

  public boolean userVerificationRequired() {
    return userVerificationRequired;
  }

  public boolean userPresenceRequired() {
    return userPresenceRequired;
  }
}
