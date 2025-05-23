/*
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
