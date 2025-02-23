package org.idp.sample.subdomain.webauthn;

import com.webauthn4j.WebAuthnManager;
import com.webauthn4j.converter.AttestedCredentialDataConverter;
import com.webauthn4j.converter.util.ObjectConverter;
import com.webauthn4j.data.RegistrationData;
import com.webauthn4j.data.RegistrationParameters;
import java.util.Objects;

public class WebAuthnRegistrationManager {

  WebAuthnManager webAuthnManager;
  WebAuthnConfiguration configuration;
  WebAuthnSession session;
  String request;
  String userId;

  public WebAuthnRegistrationManager(
      WebAuthnManager webAuthnManager,
      WebAuthnConfiguration configuration,
      WebAuthnSession session,
      String request,
      String userId) {
    this.webAuthnManager = webAuthnManager;
    this.configuration = configuration;
    this.session = session;
    this.request = request;
    this.userId = userId;
  }

  public WebAuthnCredential verifyAndCreateCredential() {

    RegistrationData registrationData = parseRequest();
    RegistrationParameters registrationParameters = configuration.toRegistrationParameters(session);

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

    return new WebAuthnCredential(
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

      throw new WebAuthnBadRequestException("webauthn verification is failed", e);
    }
  }

  private RegistrationData parseRequest() {
    try {
      return webAuthnManager.parseRegistrationResponseJSON(request);
    } catch (Exception e) {

      throw new WebAuthnBadRequestException("webauthn registration request is invalid", e);
    }
  }
}
