package org.idp.sample.subdomain.webauthn;

import com.webauthn4j.WebAuthnManager;
import com.webauthn4j.data.RegistrationData;
import com.webauthn4j.data.RegistrationParameters;
import java.util.UUID;

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

    verifyAndCreateCredential(registrationData, registrationParameters);

    return new WebAuthnCredential(
        UUID.randomUUID().toString(),
        userId,
        configuration.rpId(),
        new byte[0],
        registrationData.getAttestationObjectBytes(),
        0);
  }

  private void verifyAndCreateCredential(
      RegistrationData registrationData, RegistrationParameters registrationParameters) {
    try {
      webAuthnManager.verify(registrationData, registrationParameters);
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
