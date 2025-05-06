package org.idp.server.authenticators.webauthn4j;

import com.webauthn4j.data.PublicKeyCredentialParameters;
import com.webauthn4j.data.RegistrationParameters;
import com.webauthn4j.data.client.Origin;
import com.webauthn4j.server.ServerProperty;
import java.util.List;

public class WebAuthn4jConfiguration {
  String rpId;
  String rpName;
  String origin;
  byte[] tokenBindingId;
  String attestationPreference;
  String authenticatorAttachment;

  boolean requireResidentKey;
  boolean userVerificationRequired;
  boolean userPresenceRequired;

  public WebAuthn4jConfiguration() {}

  public WebAuthn4jConfiguration(String rpId, String rpName, String origin, byte[] tokenBindingId, String attestationPreference, String authenticatorAttachment, boolean requireResidentKey, boolean userVerificationRequired, boolean userPresenceRequired) {
    this.rpId = rpId;
    this.rpName = rpName;
    this.origin = origin;
    this.tokenBindingId = tokenBindingId;
    this.attestationPreference = attestationPreference;
    this.authenticatorAttachment = authenticatorAttachment;
    this.requireResidentKey = requireResidentKey;
    this.userVerificationRequired = userVerificationRequired;
    this.userPresenceRequired = userPresenceRequired;
  }

  // FIXME
  RegistrationParameters toRegistrationParameters(WebAuthn4jChallenge webAuthn4jChallenge) {

    // Server properties
    Origin origin = Origin.create(this.origin);
    ServerProperty serverProperty = new ServerProperty(origin, rpId, webAuthn4jChallenge, tokenBindingId);

    // expectations
    List<PublicKeyCredentialParameters> pubKeyCredParams = null;

    return new RegistrationParameters(serverProperty, pubKeyCredParams, userVerificationRequired, userPresenceRequired);
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
