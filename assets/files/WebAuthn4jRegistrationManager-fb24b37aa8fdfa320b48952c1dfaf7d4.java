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

import com.webauthn4j.WebAuthnRegistrationManager;
import com.webauthn4j.converter.AttestedCredentialDataConverter;
import com.webauthn4j.converter.util.ObjectConverter;
import com.webauthn4j.data.AuthenticatorTransport;
import com.webauthn4j.data.RegistrationData;
import com.webauthn4j.data.RegistrationParameters;
import com.webauthn4j.data.attestation.AttestationObject;
import com.webauthn4j.data.attestation.authenticator.AAGUID;
import com.webauthn4j.data.attestation.authenticator.AttestedCredentialData;
import com.webauthn4j.data.attestation.authenticator.AuthenticatorData;
import com.webauthn4j.data.attestation.statement.AttestationStatement;
import com.webauthn4j.data.attestation.statement.CertificateBaseAttestationStatement;
import com.webauthn4j.data.extension.CredentialProtectionPolicy;
import com.webauthn4j.data.extension.authenticator.AuthenticationExtensionsAuthenticatorOutputs;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.idp.server.platform.log.LoggerWrapper;

public class WebAuthn4jRegistrationManager {

  private static final LoggerWrapper log =
      LoggerWrapper.getLogger(WebAuthn4jRegistrationManager.class);

  WebAuthnRegistrationManager webAuthnRegistrationManager;
  WebAuthn4jConfiguration configuration;
  WebAuthn4jChallenge webAuthn4jChallenge;
  String request;
  String userId;
  String username;
  String displayName;

  public WebAuthn4jRegistrationManager(
      WebAuthn4jConfiguration configuration,
      WebAuthn4jChallenge webAuthn4jChallenge,
      String request,
      String userId,
      String username,
      String displayName) {
    WebAuthn4jManagerFactory factory = new WebAuthn4jManagerFactory(configuration);
    this.webAuthnRegistrationManager = factory.createRegistrationManager();
    this.configuration = configuration;
    this.webAuthn4jChallenge = webAuthn4jChallenge;
    this.request = request;
    this.userId = userId;
    this.username = username;
    this.displayName = displayName;
  }

  public WebAuthn4jCredential verifyAndCreateCredential() {

    log.debug("webauthn4j attestation verification starting for user: {}", username);

    RegistrationData registrationData = parseRequest();
    logRegistrationDataBeforeVerification(registrationData);

    RegistrationParameters registrationParameters =
        configuration.toRegistrationParameters(webAuthn4jChallenge);
    log.debug(
        "webauthn4j attestation parameters: rpId={}, origin={}, userVerificationRequired={}",
        configuration.rpId(),
        configuration.origin(),
        registrationParameters.isUserVerificationRequired());

    RegistrationData verified = verifyAndCreateCredential(registrationData, registrationParameters);
    logVerificationSuccess(verified);

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

    // Extract flags from authenticator data
    byte flags = verified.getAttestationObject().getAuthenticatorData().getFlags();
    boolean isUserPresent = (flags & 0x01) != 0; // UP bit
    boolean isUserVerified = (flags & 0x04) != 0; // UV bit
    Boolean rk = isUserPresent && isUserVerified;

    // WebAuthn Level 3: Backup Flags
    Boolean backupEligible = (flags & 0x08) != 0; // BE bit (bit 3)
    Boolean backupState = (flags & 0x10) != 0; // BS bit (bit 4)

    // Extract credProtect from authenticator extensions
    Integer credProtect = null;
    AuthenticationExtensionsAuthenticatorOutputs<?> authenticatorExtensions =
        verified.getAttestationObject().getAuthenticatorData().getExtensions();
    if (authenticatorExtensions != null && authenticatorExtensions.getCredProtect() != null) {
      CredentialProtectionPolicy policy = authenticatorExtensions.getCredProtect();
      credProtect = (int) policy.toByte(); // 0x01, 0x02, or 0x03
    }

    // Build JSON columns

    // authenticator JSON: transports, attachment
    Map<String, Object> authenticatorJson = new HashMap<>();
    authenticatorJson.put("transports", transports);
    // attachment can be inferred from transports (internal = platform, others = cross-platform)
    if (transports.contains("internal")) {
      authenticatorJson.put("attachment", "platform");
    } else if (!transports.isEmpty()) {
      authenticatorJson.put("attachment", "cross-platform");
    }

    // attestation JSON: type, format
    Map<String, Object> attestationJson = new HashMap<>();
    attestationJson.put("type", attestationType);
    attestationJson.put("format", attestationType);

    // extensions JSON: cred_protect, etc.
    Map<String, Object> extensionsJson = new HashMap<>();
    if (credProtect != null) {
      extensionsJson.put("cred_protect", credProtect);
    }

    // device JSON: for future use (device name, registration context)
    Map<String, Object> deviceJson = new HashMap<>();

    // metadata JSON: for future extensions
    Map<String, Object> metadataJson = new HashMap<>();

    // Current timestamp
    Long createdAt = System.currentTimeMillis();

    return new WebAuthn4jCredential(
        id,
        userId,
        username, // username from registration challenge request
        displayName, // user_display_name from registration challenge request
        configuration.rpId(),
        aaguidString,
        attestationDataString,
        signatureAlgorithm,
        0, // signCount starts at 0
        rk,
        backupEligible, // WebAuthn Level 3 BE flag
        backupState, // WebAuthn Level 3 BS flag
        authenticatorJson, // JSON: transports, attachment
        attestationJson, // JSON: attestation type/format
        extensionsJson, // JSON: cred_protect, etc.
        deviceJson, // JSON: device info (future)
        metadataJson, // JSON: metadata (future)
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
      log.debug("webauthn4j starting attestation verification");
      return webAuthnRegistrationManager.verify(registrationData, registrationParameters);
    } catch (Exception e) {
      log.debug(
          "webauthn4j attestation verification failed: {} - {}",
          e.getClass().getSimpleName(),
          e.getMessage());
      throw new WebAuthn4jBadRequestException("webauthn verification is failed", e);
    }
  }

  private RegistrationData parseRequest() {
    try {
      RegistrationData data = webAuthnRegistrationManager.parse(request);
      log.debug("webauthn4j registration request parsed successfully");
      return data;
    } catch (Exception e) {
      log.debug("webauthn4j registration request parse failed: {}", e.getMessage());
      throw new WebAuthn4jBadRequestException("webauthn registration request is invalid", e);
    }
  }

  private void logRegistrationDataBeforeVerification(RegistrationData registrationData) {
    AttestationObject attestationObject = registrationData.getAttestationObject();
    if (attestationObject == null) {
      log.debug("webauthn4j attestation object is null");
      return;
    }

    AttestationStatement attestationStatement = attestationObject.getAttestationStatement();
    String format = attestationStatement != null ? attestationStatement.getFormat() : "unknown";
    log.debug("webauthn4j attestation format: {}", format);

    // Log certificate chain info if present
    if (attestationStatement instanceof CertificateBaseAttestationStatement certStatement) {
      var x5c = certStatement.getX5c();
      if (x5c != null && !x5c.isEmpty()) {
        log.debug("webauthn4j attestation certificate chain length: {}", x5c.size());
        for (int i = 0; i < x5c.size(); i++) {
          X509Certificate cert = x5c.get(i);
          log.debug(
              "webauthn4j attestation cert[{}]: subject={}, issuer={}",
              i,
              cert.getSubjectX500Principal().getName(),
              cert.getIssuerX500Principal().getName());
        }
      } else {
        log.debug("webauthn4j attestation has no certificate chain (self-attestation or none)");
      }
    }

    AuthenticatorData<?> authData = attestationObject.getAuthenticatorData();
    if (authData != null) {
      AttestedCredentialData attestedCredData = authData.getAttestedCredentialData();
      if (attestedCredData != null) {
        AAGUID aaguid = attestedCredData.getAaguid();
        log.debug(
            "webauthn4j authenticator AAGUID: {}", aaguid != null ? aaguid.toString() : "null");
      }

      byte flags = authData.getFlags();
      log.debug(
          "webauthn4j authenticator flags: UP={}, UV={}, AT={}, ED={}, BE={}, BS={}",
          (flags & 0x01) != 0, // User Present
          (flags & 0x04) != 0, // User Verified
          (flags & 0x40) != 0, // Attested credential data included
          (flags & 0x80) != 0, // Extension data included
          (flags & 0x08) != 0, // Backup Eligible
          (flags & 0x10) != 0); // Backup State
    }
  }

  private void logVerificationSuccess(RegistrationData verified) {
    AttestationObject attestationObject = verified.getAttestationObject();
    if (attestationObject == null) {
      return;
    }

    AttestationStatement attestationStatement = attestationObject.getAttestationStatement();
    String format = attestationStatement != null ? attestationStatement.getFormat() : "none";

    AttestedCredentialData attestedCredData =
        attestationObject.getAuthenticatorData().getAttestedCredentialData();
    String aaguid =
        attestedCredData != null && attestedCredData.getAaguid() != null
            ? attestedCredData.getAaguid().toString()
            : "unknown";

    log.debug(
        "webauthn4j attestation verification succeeded: format={}, aaguid={}", format, aaguid);

    // Warn if "direct" attestation was expected but "none" was received
    String attestationPreference = configuration.attestationPreference();
    if ("direct".equalsIgnoreCase(attestationPreference)
        || "enterprise".equalsIgnoreCase(attestationPreference)) {
      if ("none".equals(format)) {
        log.warn(
            "webauthn4j attestation: requested '{}' but received 'none'. "
                + "Platform authenticators (Touch ID/Face ID) may not support attestation. "
                + "AAGUID: {}, consider using a security key for full attestation.",
            attestationPreference,
            aaguid);
      }
    }

    // Log if certificate chain was verified
    if (attestationStatement instanceof CertificateBaseAttestationStatement certStatement) {
      var x5c = certStatement.getX5c();
      if (x5c != null && !x5c.isEmpty()) {
        log.debug("webauthn4j certificate chain verification passed ({} certs)", x5c.size());
      }
    }

    Set<AuthenticatorTransport> transports = verified.getTransports();
    if (transports != null && !transports.isEmpty()) {
      log.debug(
          "webauthn4j authenticator transports: {}",
          transports.stream().map(AuthenticatorTransport::getValue).collect(Collectors.toList()));
    }
  }
}
