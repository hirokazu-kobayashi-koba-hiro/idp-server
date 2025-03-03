package org.idp.server.subdomain.webauthn;

import com.webauthn4j.WebAuthnManager;
import com.webauthn4j.credential.CredentialRecordImpl;
import com.webauthn4j.data.AuthenticationData;
import com.webauthn4j.data.AuthenticationParameters;
import com.webauthn4j.data.client.Origin;
import com.webauthn4j.server.ServerProperty;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

public class WebAuthnAuthenticationManager {

  WebAuthnManager webAuthnManager;
  WebAuthnConfiguration configuration;
  WebAuthnSession session;
  String request;

  public WebAuthnAuthenticationManager(
      WebAuthnManager webAuthnManager,
      WebAuthnConfiguration configuration,
      WebAuthnSession session,
      String request) {
    this.webAuthnManager = webAuthnManager;
    this.configuration = configuration;
    this.session = session;
    this.request = request;
  }

  public String extractUserId() {
    AuthenticationData authenticationData = parseAuthenticationData();

    return new String(
        Objects.requireNonNull(authenticationData.getUserHandle()), StandardCharsets.UTF_8);
  }

  public void verify(WebAuthnCredentials credentials) {
    AuthenticationData authenticationData = parseAuthenticationData();

    WebAuthnCredential credential = credentials.get(configuration.rpId());
    WebAuthnCredentialConverter webAuthnCredentialConverter =
        new WebAuthnCredentialConverter(credential);
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
      throw new WebAuthnBadRequestException("Failed to verify authentication data", e);
    }
  }

  private AuthenticationData parseAuthenticationData() {
    try {
      return webAuthnManager.parseAuthenticationResponseJSON(request);
    } catch (Exception e) {
      throw new WebAuthnBadRequestException("Failed to parse authentication response", e);
    }
  }

  private AuthenticationParameters toAuthenticationParameters(
      CredentialRecordImpl credentialRecord) {
    // Server properties
    Origin origin = Origin.create(configuration.origin());
    byte[] tokenBindingId = null;
    ServerProperty serverProperty =
        new ServerProperty(origin, configuration.rpId(), session.challenge(), tokenBindingId);

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
