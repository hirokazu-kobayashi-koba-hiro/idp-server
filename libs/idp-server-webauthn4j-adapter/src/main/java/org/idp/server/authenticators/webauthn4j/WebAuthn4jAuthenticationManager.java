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
import com.webauthn4j.credential.CredentialRecordImpl;
import com.webauthn4j.data.AuthenticationData;
import com.webauthn4j.data.AuthenticationParameters;
import com.webauthn4j.data.client.Origin;
import com.webauthn4j.server.ServerProperty;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class WebAuthn4jAuthenticationManager {

  WebAuthnManager webAuthnManager;
  WebAuthn4jConfiguration configuration;
  WebAuthn4jChallenge challenge;
  String request;

  public WebAuthn4jAuthenticationManager(
      WebAuthn4jConfiguration configuration, WebAuthn4jChallenge challenge, String request) {
    this.webAuthnManager = WebAuthnManager.createNonStrictWebAuthnManager();
    this.configuration = configuration;
    this.challenge = challenge;
    this.request = request;
  }

  public AuthenticationData verifyAndGetAuthenticationData(WebAuthn4jCredential credential) {
    AuthenticationData parsedData = parseAuthenticationData();

    WebAuthn4jCredentialConverter webAuthnCredentialConverter =
        new WebAuthn4jCredentialConverter(credential);
    CredentialRecordImpl credentialRecord = webAuthnCredentialConverter.convert();

    AuthenticationParameters authenticationParameters =
        toAuthenticationParameters(credentialRecord);

    return verifyAuthenticationData(parsedData, authenticationParameters);
  }

  private AuthenticationData verifyAuthenticationData(
      AuthenticationData authenticationData, AuthenticationParameters authenticationParameters) {
    try {
      return webAuthnManager.verify(authenticationData, authenticationParameters);
    } catch (Exception e) {
      throw new WebAuthn4jBadRequestException("Failed to verify authentication data", e);
    }
  }

  private AuthenticationData parseAuthenticationData() {
    try {
      return webAuthnManager.parseAuthenticationResponseJSON(request);
    } catch (Exception e) {
      throw new WebAuthn4jBadRequestException("Failed to parse authentication response", e);
    }
  }

  private AuthenticationParameters toAuthenticationParameters(
      CredentialRecordImpl credentialRecord) {
    Set<Origin> origins =
        configuration.getAllowedOrigins().stream().map(Origin::create).collect(Collectors.toSet());

    ServerProperty serverProperty =
        ServerProperty.builder()
            .origins(origins)
            .rpId(configuration.rpId())
            .challenge(challenge)
            .build();

    List<byte[]> allowCredentials = null;
    boolean userVerificationRequired = configuration.userVerificationRequired();
    boolean userPresenceRequired = configuration.userPresenceRequired();

    return new AuthenticationParameters(
        serverProperty,
        credentialRecord,
        allowCredentials,
        userVerificationRequired,
        userPresenceRequired);
  }
}
