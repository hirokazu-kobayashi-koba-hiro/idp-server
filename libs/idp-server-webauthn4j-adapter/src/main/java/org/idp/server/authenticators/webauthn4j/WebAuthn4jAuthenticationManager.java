package org.idp.server.authenticators.webauthn4j;

import com.webauthn4j.WebAuthnManager;
import com.webauthn4j.credential.CredentialRecordImpl;
import com.webauthn4j.data.AuthenticationData;
import com.webauthn4j.data.AuthenticationParameters;
import com.webauthn4j.data.client.Origin;
import com.webauthn4j.server.ServerProperty;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

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

  public String extractUserId() {
    AuthenticationData authenticationData = parseAuthenticationData();

    return new String(
        Objects.requireNonNull(authenticationData.getUserHandle()), StandardCharsets.UTF_8);
  }

  public void verify(WebAuthn4jCredentials credentials) {
    AuthenticationData authenticationData = parseAuthenticationData();

    WebAuthn4jCredential credential = credentials.get(configuration.rpId());
    WebAuthn4jCredentialConverter webAuthnCredentialConverter =
        new WebAuthn4jCredentialConverter(credential);
    CredentialRecordImpl credentialRecord = webAuthnCredentialConverter.convert();

    AuthenticationParameters authenticationParameters =
        toAuthenticationParameters(credentialRecord);

    AuthenticationData verifiedData =
        verifyAuthenticationData(authenticationData, authenticationParameters);
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
    // Server properties
    Origin origin = Origin.create(configuration.origin());
    byte[] tokenBindingId = null;
    ServerProperty serverProperty =
        new ServerProperty(origin, configuration.rpId(), challenge, tokenBindingId);

    // expectations
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
