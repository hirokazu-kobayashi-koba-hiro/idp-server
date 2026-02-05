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
import com.webauthn4j.anchor.KeyStoreTrustAnchorRepository;
import com.webauthn4j.anchor.TrustAnchorRepository;
import com.webauthn4j.converter.util.ObjectConverter;
import com.webauthn4j.metadata.FidoMDS3MetadataBLOBProvider;
import com.webauthn4j.metadata.anchor.MetadataBLOBBasedTrustAnchorRepository;
import com.webauthn4j.verifier.attestation.statement.AttestationStatementVerifier;
import com.webauthn4j.verifier.attestation.statement.androidkey.AndroidKeyAttestationStatementVerifier;
import com.webauthn4j.verifier.attestation.statement.androidsafetynet.AndroidSafetyNetAttestationStatementVerifier;
import com.webauthn4j.verifier.attestation.statement.none.NoneAttestationStatementVerifier;
import com.webauthn4j.verifier.attestation.statement.packed.PackedAttestationStatementVerifier;
import com.webauthn4j.verifier.attestation.statement.tpm.TPMAttestationStatementVerifier;
import com.webauthn4j.verifier.attestation.statement.u2f.FIDOU2FAttestationStatementVerifier;
import com.webauthn4j.verifier.attestation.trustworthiness.certpath.CertPathTrustworthinessVerifier;
import com.webauthn4j.verifier.attestation.trustworthiness.certpath.DefaultCertPathTrustworthinessVerifier;
import com.webauthn4j.verifier.attestation.trustworthiness.certpath.NullCertPathTrustworthinessVerifier;
import com.webauthn4j.verifier.attestation.trustworthiness.self.DefaultSelfAttestationTrustworthinessVerifier;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.idp.server.authenticators.webauthn4j.mds.MdsConfiguration;
import org.idp.server.platform.log.LoggerWrapper;

/**
 * Factory for creating WebAuthnRegistrationManager with proper attestation verification.
 *
 * <p>Attestation verification levels:
 *
 * <ul>
 *   <li>No TrustStore/MDS configured: NullCertPathTrustworthinessVerifier (skip cert chain
 *       verification)
 *   <li>TrustStore configured: KeyStoreTrustAnchorRepository (static trust anchors)
 *   <li>MDS configured: MetadataBLOBBasedTrustAnchorRepository (FIDO MDS based trust anchors)
 * </ul>
 *
 * <p>Supported attestation formats (following Keycloak's approach):
 *
 * <ul>
 *   <li>none - No attestation
 *   <li>packed - General attestation format
 *   <li>tpm - Windows Hello, etc.
 *   <li>android-key - Android Hardware Keystore
 *   <li>android-safetynet - Android SafetyNet
 *   <li>fido-u2f - FIDO U2F compatible
 * </ul>
 */
public class WebAuthn4jManagerFactory {

  private static final LoggerWrapper log = LoggerWrapper.getLogger(WebAuthn4jManagerFactory.class);

  private final WebAuthn4jConfiguration configuration;
  private final MdsConfiguration mdsConfiguration;
  private KeyStore trustStore;

  public WebAuthn4jManagerFactory(WebAuthn4jConfiguration configuration) {
    this(configuration, null);
  }

  public WebAuthn4jManagerFactory(
      WebAuthn4jConfiguration configuration, MdsConfiguration mdsConfiguration) {
    this.configuration = configuration;
    this.mdsConfiguration = mdsConfiguration;
    this.trustStore = loadTrustStore();
  }

  /** Creates a WebAuthnRegistrationManager with appropriate attestation verification. */
  public WebAuthnRegistrationManager createRegistrationManager() {
    List<AttestationStatementVerifier> verifiers = createAttestationStatementVerifiers();
    CertPathTrustworthinessVerifier certPathVerifier = createCertPathTrustworthinessVerifier();

    return new WebAuthnRegistrationManager(
        verifiers,
        certPathVerifier,
        new DefaultSelfAttestationTrustworthinessVerifier(),
        Collections.emptyList(),
        new ObjectConverter());
  }

  /**
   * Creates attestation statement verifiers based on attestation conveyance preference.
   *
   * <p>Note: NoneAttestationStatementVerifier is always added because many platform authenticators
   * (macOS Touch ID, iOS Face ID) return "none" attestation even when "direct" is requested. This
   * is by design for privacy protection. The actual attestation type validation (e.g., requiring
   * non-none attestation for high-security scenarios) should be done at the application level after
   * registration succeeds.
   */
  private List<AttestationStatementVerifier> createAttestationStatementVerifiers() {
    List<AttestationStatementVerifier> verifiers = new ArrayList<>(6);

    String attestationPreference = configuration.attestationPreference();

    // Always add NoneVerifier - platform authenticators may return "none" even when "direct"
    // is requested (privacy protection by design on macOS/iOS)
    verifiers.add(new NoneAttestationStatementVerifier());

    // Always add other attestation verifiers
    verifiers.add(new PackedAttestationStatementVerifier());
    verifiers.add(new TPMAttestationStatementVerifier());
    verifiers.add(new AndroidKeyAttestationStatementVerifier());
    verifiers.add(new AndroidSafetyNetAttestationStatementVerifier());
    verifiers.add(new FIDOU2FAttestationStatementVerifier());

    log.debug(
        "Created attestation statement verifiers: attestationPreference={}, verifierCount={}",
        attestationPreference,
        verifiers.size());

    return verifiers;
  }

  /**
   * Creates certificate path trustworthiness verifier.
   *
   * <p>Priority order:
   *
   * <ol>
   *   <li>MDS BLOB based verification (if MDS is configured and enabled)
   *   <li>KeyStore based verification (if TrustStore is configured)
   *   <li>Null verification (skip certificate chain verification)
   * </ol>
   */
  private CertPathTrustworthinessVerifier createCertPathTrustworthinessVerifier() {
    // Try MDS-based verification first
    TrustAnchorRepository mdsRepository = createMdsBasedTrustAnchorRepository();
    if (mdsRepository != null) {
      log.info("MDS BLOB configured, using MetadataBLOBBasedTrustAnchorRepository");
      return new DefaultCertPathTrustworthinessVerifier(mdsRepository);
    }

    // Fall back to KeyStore-based verification
    if (trustStore != null) {
      log.info("TrustStore configured, certificate chain verification is enabled");
      KeyStoreTrustAnchorRepository trustAnchorRepository =
          new KeyStoreTrustAnchorRepository(trustStore);
      return new DefaultCertPathTrustworthinessVerifier(trustAnchorRepository);
    }

    log.info("No TrustStore or MDS configured, certificate chain verification will be skipped");
    return new NullCertPathTrustworthinessVerifier();
  }

  /** Creates MDS-based TrustAnchorRepository if MDS is configured. */
  private TrustAnchorRepository createMdsBasedTrustAnchorRepository() {
    if (mdsConfiguration == null || !mdsConfiguration.enabled()) {
      return null;
    }

    try {
      ObjectConverter objectConverter = new ObjectConverter();
      Set<TrustAnchor> trustAnchors = getDefaultTrustAnchors();
      FidoMDS3MetadataBLOBProvider provider =
          new FidoMDS3MetadataBLOBProvider(objectConverter, trustAnchors);
      MetadataBLOBBasedTrustAnchorRepository repository =
          new MetadataBLOBBasedTrustAnchorRepository(provider);

      log.info("MDS BLOB provider configured for network fetch from FIDO MDS");
      return repository;

    } catch (Exception e) {
      log.error("Failed to configure MDS BLOB provider", e);
      return null;
    }
  }

  /** Gets default trust anchors for FIDO MDS BLOB verification. */
  private Set<TrustAnchor> getDefaultTrustAnchors() {
    Set<TrustAnchor> trustAnchors = new HashSet<>();
    try {
      // Load FIDO MDS root certificate from classpath
      try (InputStream is =
          getClass().getClassLoader().getResourceAsStream("metadata/mds-root.crt")) {
        if (is != null) {
          CertificateFactory cf = CertificateFactory.getInstance("X.509");
          X509Certificate rootCert = (X509Certificate) cf.generateCertificate(is);
          trustAnchors.add(new TrustAnchor(rootCert, null));
          log.debug("Loaded FIDO MDS root certificate from classpath");
        }
      }
    } catch (Exception e) {
      log.warn("Could not load FIDO MDS root certificate, using system defaults", e);
    }

    // If no custom cert loaded, return empty set (provider will use defaults)
    return trustAnchors;
  }

  /** Loads TrustStore from configured path. */
  private KeyStore loadTrustStore() {
    if (!configuration.hasTrustStore()) {
      return null;
    }

    String path = configuration.trustStorePath();
    String password = configuration.trustStorePassword();
    String type = configuration.trustStoreType();

    try {
      KeyStore keyStore = KeyStore.getInstance(type);
      try (FileInputStream fis = new FileInputStream(path)) {
        keyStore.load(fis, password != null ? password.toCharArray() : null);
      }
      log.info("Loaded TrustStore from path: {}, type: {}", path, type);
      return keyStore;
    } catch (Exception e) {
      log.error("Failed to load TrustStore from path: {}", path, e);
      throw new WebAuthn4jBadRequestException("Failed to load TrustStore: " + e.getMessage(), e);
    }
  }
}
