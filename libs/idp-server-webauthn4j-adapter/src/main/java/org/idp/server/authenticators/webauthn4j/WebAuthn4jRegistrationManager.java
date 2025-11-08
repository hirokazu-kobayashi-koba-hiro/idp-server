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
import com.webauthn4j.data.AuthenticatorTransport;
import com.webauthn4j.data.RegistrationData;
import com.webauthn4j.data.RegistrationParameters;
import com.webauthn4j.data.attestation.authenticator.AAGUID;
import com.webauthn4j.data.attestation.authenticator.AttestedCredentialData;
import com.webauthn4j.data.attestation.statement.AttestationStatement;
import com.webauthn4j.data.extension.CredentialProtectionPolicy;
import com.webauthn4j.data.extension.authenticator.AuthenticationExtensionsAuthenticatorOutputs;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

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

    AttestedCredentialData attestedCredentialDataObj =
        Objects.requireNonNull(
            Objects.requireNonNull(verified.getAttestationObject())
                .getAuthenticatorData()
                .getAttestedCredentialData());

    byte[] attestedCredentialData =
        attestedCredentialDataConverter.convert(attestedCredentialDataObj);

    Base64.Encoder urlEncoder = Base64.getUrlEncoder().withoutPadding();
    String id = urlEncoder.encodeToString(credentialId);
    String attestationDataString = urlEncoder.encodeToString(attestedCredentialData);

    // Extract AAGUID
    AAGUID aaguid = attestedCredentialDataObj.getAaguid();
    String aaguidString =
        aaguid != null ? aaguid.toString() : "00000000-0000-0000-0000-000000000000";

    // Extract Transports
    Set<AuthenticatorTransport> transportsSet = verified.getTransports();
    List<String> transports =
        transportsSet != null
            ? transportsSet.stream()
                .map(AuthenticatorTransport::getValue)
                .collect(Collectors.toList())
            : new ArrayList<>();

    // Extract Signature Algorithm
    Integer signatureAlgorithm =
        (int) attestedCredentialDataObj.getCOSEKey().getAlgorithm().getValue();

    // Extract Attestation Type
    AttestationStatement attestationStatement =
        verified.getAttestationObject().getAttestationStatement();
    String attestationType =
        attestationStatement != null ? attestationStatement.getFormat() : "none";

    // Extract Resident Key flag from authenticator data
    byte flags = verified.getAttestationObject().getAuthenticatorData().getFlags();
    boolean isUserPresent = (flags & 0x01) != 0; // UP bit
    boolean isUserVerified = (flags & 0x04) != 0; // UV bit
    Boolean rk = isUserPresent && isUserVerified;

    // Extract credProtect from authenticator extensions
    Integer credProtect = null;
    AuthenticationExtensionsAuthenticatorOutputs<?> authenticatorExtensions =
        verified.getAttestationObject().getAuthenticatorData().getExtensions();
    if (authenticatorExtensions != null && authenticatorExtensions.getCredProtect() != null) {
      CredentialProtectionPolicy policy = authenticatorExtensions.getCredProtect();
      credProtect = (int) policy.toByte(); // 0x01, 0x02, or 0x03
    }

    // Current timestamp
    Long createdAt = System.currentTimeMillis();

    return new WebAuthn4jCredential(
        id,
        userId,
        null, // username - not available in registration data
        null, // userDisplayName - not available in registration data
        null, // userIcon - not available in registration data
        configuration.rpId(),
        aaguidString,
        attestationDataString,
        signatureAlgorithm,
        0, // signCount starts at 0
        attestationType,
        rk,
        credProtect, // Extracted from authenticator extensions
        transports,
        createdAt,
        null, // updatedAt - will be set on first update
        null // authenticatedAt - will be set on first authentication
        );
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
