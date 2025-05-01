package org.idp.server.authenticators.webauthn4j;

import com.webauthn4j.WebAuthnManager;
import com.webauthn4j.converter.AttestedCredentialDataConverter;
import com.webauthn4j.converter.util.ObjectConverter;
import com.webauthn4j.data.RegistrationData;
import com.webauthn4j.data.RegistrationParameters;
import java.util.Objects;

public class WebAuthn4jRegistrationManager {

  WebAuthnManager webAuthnManager;
  WebAuthn4jConfiguration configuration;
  WebAuthn4jChallenge webAuthn4jChallenge;
  String request;
  String userId;

  public WebAuthn4jRegistrationManager(
      WebAuthn4jConfiguration configuration,
      WebAuthn4jChallenge webAuthn4jChallenge,
      String request,
      String userId) {
    this.webAuthnManager = WebAuthnManager.createNonStrictWebAuthnManager();
    this.configuration = configuration;
    this.webAuthn4jChallenge = webAuthn4jChallenge;
    this.request = request;
    this.userId = userId;
  }

  public WebAuthn4jCredential verifyAndCreateCredential() {

    RegistrationData registrationData = parseRequest();
    RegistrationParameters registrationParameters =
        configuration.toRegistrationParameters(webAuthn4jChallenge);

    RegistrationData verified = verifyAndCreateCredential(registrationData, registrationParameters);
    byte[] credentialId = credentialId(verified);

    ObjectConverter objectConverter = new ObjectConverter();
    AttestedCredentialDataConverter attestedCredentialDataConverter =
        new AttestedCredentialDataConverter(objectConverter);
    byte[] attestedCredentialData =
        attestedCredentialDataConverter.convert(
            Objects.requireNonNull(
                Objects.requireNonNull(verified.getAttestationObject())
                    .getAuthenticatorData()
                    .getAttestedCredentialData()));

    return new WebAuthn4jCredential(
        credentialId, userId, configuration.rpId(), new byte[0], attestedCredentialData, 0);
  }

  private byte[] credentialId(RegistrationData verified) {
    try {

      return Objects.requireNonNull(
              Objects.requireNonNull(verified.getAttestationObject())
                  .getAuthenticatorData()
                  .getAttestedCredentialData())
          .getCredentialId();
    } catch (Exception e) {

      throw new RuntimeException(e);
    }
  }

  private RegistrationData verifyAndCreateCredential(
      RegistrationData registrationData, RegistrationParameters registrationParameters) {
    try {
      return webAuthnManager.verify(registrationData, registrationParameters);
    } catch (Exception e) {

      throw new WebAuthn4jBadRequestException("webauthn verification is failed", e);
    }
  }

  private RegistrationData parseRequest() {
    try {
      return webAuthnManager.parseRegistrationResponseJSON(request);
    } catch (Exception e) {

      throw new WebAuthn4jBadRequestException("webauthn registration request is invalid", e);
    }
  }
}
