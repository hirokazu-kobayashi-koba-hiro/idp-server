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

import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * WebAuthn credential data model representing a registered FIDO2 authenticator.
 *
 * <p>This class stores credential information extracted from WebAuthn registration responses and
 * manages authenticator metadata for subsequent authentication operations.
 *
 * <h2>User Experience Impact Parameters</h2>
 *
 * <p>The following parameters directly affect user authentication behavior and security policy:
 *
 * <h3>1. Resident Key (rk)</h3>
 *
 * <ul>
 *   <li><b>Purpose:</b> Determines if credential is discoverable (client-side discoverable
 *       credential)
 *   <li><b>Set by:</b> Server via {@code authenticatorSelection.residentKey} in registration
 *       request
 *   <li><b>Values:</b>
 *       <ul>
 *         <li>{@code true}: Credential stored on authenticator (passwordless login possible)
 *         <li>{@code false}: Credential ID must be provided by server (username required)
 *       </ul>
 *   <li><b>User Impact:</b>
 *       <ul>
 *         <li>rk=true → User can authenticate without entering username
 *         <li>rk=false → User must enter username first, then authenticate
 *       </ul>
 *   <li><b>Server Configuration:</b>
 *       <pre>{@code
 * {
 *   "authenticatorSelection": {
 *     "residentKey": "required",  // Forces rk=true
 *     "requireResidentKey": true
 *   }
 * }
 * }</pre>
 * </ul>
 *
 * <h3>2. Credential Protection Policy (credProtect)</h3>
 *
 * <ul>
 *   <li><b>Purpose:</b> Defines user verification requirements for credential usage
 *   <li><b>Set by:</b> Client via {@code extensions.credProtect} in registration request
 *   <li><b>Decision maker:</b> Authenticator (may downgrade if unsupported)
 *   <li><b>Values:</b>
 *       <ul>
 *         <li>{@code 0x01} (1): USER_VERIFICATION_OPTIONAL - UV not required
 *         <li>{@code 0x02} (2): USER_VERIFICATION_OPTIONAL_WITH_CREDENTIAL_ID_LIST - UV optional
 *             only if credential is discoverable
 *         <li>{@code 0x03} (3): USER_VERIFICATION_REQUIRED - UV always required
 *       </ul>
 *   <li><b>User Impact:</b>
 *       <ul>
 *         <li>credProtect=1 → No PIN/biometric required (presence only)
 *         <li>credProtect=2 → PIN/biometric required for passwordless login
 *         <li>credProtect=3 → PIN/biometric always required
 *       </ul>
 *   <li><b>Client Configuration (JavaScript):</b>
 *       <pre>{@code
 * navigator.credentials.create({
 *   publicKey: {
 *     extensions: {
 *       credProtect: 2,  // Request level 2
 *       enforceCredentialProtectionPolicy: false  // Allow downgrade
 *     }
 *   }
 * })
 * }</pre>
 *   <li><b>Note:</b> Server cannot force credProtect level; can only validate after registration
 * </ul>
 *
 * <h3>3. User Verification (UV)</h3>
 *
 * <ul>
 *   <li><b>Purpose:</b> Requires biometric/PIN verification during authentication
 *   <li><b>Set by:</b> Server via {@code authenticatorSelection.userVerification} in registration
 *       request
 *   <li><b>Values:</b>
 *       <ul>
 *         <li>{@code "required"}: Always require UV (forces PIN/biometric)
 *         <li>{@code "preferred"}: Request UV but allow fallback
 *         <li>{@code "discouraged"}: Presence only (tap authenticator)
 *       </ul>
 *   <li><b>User Impact:</b>
 *       <ul>
 *         <li>required → Must provide PIN/biometric every time
 *         <li>preferred → PIN/biometric if available, otherwise tap only
 *         <li>discouraged → Just tap authenticator (no PIN/biometric)
 *       </ul>
 *   <li><b>Server Configuration:</b>
 *       <pre>{@code
 * {
 *   "authenticatorSelection": {
 *     "userVerification": "required"
 *   }
 * }
 * }</pre>
 * </ul>
 *
 * <h3>4. Authenticator Attachment</h3>
 *
 * <ul>
 *   <li><b>Purpose:</b> Restricts authenticator type (platform vs cross-platform)
 *   <li><b>Set by:</b> Server via {@code authenticatorSelection.authenticatorAttachment}
 *   <li><b>Values:</b>
 *       <ul>
 *         <li>{@code "platform"}: Device-bound (TouchID, FaceID, Windows Hello)
 *         <li>{@code "cross-platform"}: Removable (USB key, NFC, Bluetooth)
 *         <li>{@code null}: Allow both
 *       </ul>
 *   <li><b>User Impact:</b>
 *       <ul>
 *         <li>platform → Use device biometric/PIN only
 *         <li>cross-platform → Use security key only
 *         <li>null → User can choose between device biometric or security key
 *       </ul>
 *   <li><b>Server Configuration:</b>
 *       <pre>{@code
 * {
 *   "authenticatorSelection": {
 *     "authenticatorAttachment": "platform"  // Force biometric
 *   }
 * }
 * }</pre>
 * </ul>
 *
 * <h3>5. Transports</h3>
 *
 * <ul>
 *   <li><b>Purpose:</b> Communication methods supported by authenticator
 *   <li><b>Set by:</b> Authenticator during registration
 *   <li><b>Values:</b> {@code ["usb", "nfc", "ble", "internal", "hybrid"]}
 *   <li><b>User Impact:</b>
 *       <ul>
 *         <li>Browser uses transports to optimize authenticator selection UI
 *         <li>internal → Shows platform biometric prompt
 *         <li>usb → Shows "Insert security key" prompt
 *         <li>hybrid → Shows QR code for phone authentication
 *       </ul>
 *   <li><b>Note:</b> Automatically detected by authenticator; server stores for UX optimization
 * </ul>
 *
 * <h2>Configuration Responsibility Matrix</h2>
 *
 * <table border="1">
 *   <tr>
 *     <th>Parameter</th>
 *     <th>Set by Server</th>
 *     <th>Set by Client</th>
 *     <th>Decided by Authenticator</th>
 *   </tr>
 *   <tr>
 *     <td>rk (Resident Key)</td>
 *     <td>✅ authenticatorSelection.residentKey</td>
 *     <td>❌</td>
 *     <td>✅ (Final decision based on capability)</td>
 *   </tr>
 *   <tr>
 *     <td>credProtect</td>
 *     <td>❌ (Can validate after)</td>
 *     <td>✅ extensions.credProtect</td>
 *     <td>✅ (Final decision, may downgrade)</td>
 *   </tr>
 *   <tr>
 *     <td>userVerification</td>
 *     <td>✅ authenticatorSelection.userVerification</td>
 *     <td>❌</td>
 *     <td>✅ (Must comply if capable)</td>
 *   </tr>
 *   <tr>
 *     <td>authenticatorAttachment</td>
 *     <td>✅ authenticatorSelection.authenticatorAttachment</td>
 *     <td>❌</td>
 *     <td>N/A</td>
 *   </tr>
 *   <tr>
 *     <td>transports</td>
 *     <td>❌</td>
 *     <td>❌</td>
 *     <td>✅ (Auto-detected)</td>
 *   </tr>
 * </table>
 *
 * <h2>Common User Experience Patterns</h2>
 *
 * <h3>Passwordless Login (High Security)</h3>
 *
 * <pre>{@code
 * {
 *   "authenticatorSelection": {
 *     "residentKey": "required",        // rk=true
 *     "userVerification": "required"    // UV always required
 *   }
 * }
 * // Result: User taps key → Enters PIN/biometric → Authenticated
 * }</pre>
 *
 * <h3>2nd Factor (Security Key Only)</h3>
 *
 * <pre>{@code
 * {
 *   "authenticatorSelection": {
 *     "residentKey": "discouraged",          // rk=false
 *     "userVerification": "discouraged",     // Presence only
 *     "authenticatorAttachment": "cross-platform"
 *   }
 * }
 * // Result: User enters username → Taps security key → Authenticated
 * }</pre>
 *
 * <h3>Biometric-Only (Platform)</h3>
 *
 * <pre>{@code
 * {
 *   "authenticatorSelection": {
 *     "residentKey": "required",        // rk=true
 *     "userVerification": "required",   // Biometric required
 *     "authenticatorAttachment": "platform"
 *   }
 * }
 * // Result: User sees biometric prompt → FaceID/TouchID → Authenticated
 * }</pre>
 *
 * @see com.webauthn4j.data.extension.CredentialProtectionPolicy
 * @see com.webauthn4j.data.AuthenticatorTransport
 * @see <a href="https://www.w3.org/TR/webauthn-2/">W3C WebAuthn Level 2 Specification</a>
 * @see <a
 *     href="https://fidoalliance.org/specs/fido-v2.1-ps-20210615/fido-client-to-authenticator-protocol-v2.1-ps-errata-20220621.html#sctn-credProtect-extension">FIDO
 *     CTAP2 credProtect Extension</a>
 */
public class WebAuthn4jCredential {
  // Credential Identifier
  String id;

  // User Information
  String userId;
  String username;
  String userDisplayName;

  // Relying Party
  String rpId;

  // Core Authenticator Information
  String aaguid;
  String attestedCredentialData;
  Integer signatureAlgorithm;
  long signCount;

  // Core WebAuthn Features
  Boolean rk;

  // WebAuthn Level 3: Backup Flags
  Boolean backupEligible;
  Boolean backupState;

  // JSON: Authenticator metadata (transports, attachment)
  Map<String, Object> authenticator;

  // JSON: Attestation information (type, format)
  Map<String, Object> attestation;

  // JSON: WebAuthn extensions (credProtect, prf, largeBlob)
  Map<String, Object> extensions;

  // JSON: Device/registration context
  Map<String, Object> device;

  // JSON: Future extensions
  Map<String, Object> metadata;

  // Timestamps
  Long createdAt;
  Long updatedAt;
  Long authenticatedAt;

  private static final Base64.Decoder urlDecoder = Base64.getUrlDecoder();

  public WebAuthn4jCredential() {
    this.authenticator = new HashMap<>();
    this.attestation = new HashMap<>();
    this.extensions = new HashMap<>();
    this.device = new HashMap<>();
    this.metadata = new HashMap<>();
  }

  public WebAuthn4jCredential(
      String id, String userId, String rpId, String attestedCredentialData, long signCount) {
    this();
    this.id = id;
    this.userId = userId;
    this.rpId = rpId;
    this.attestedCredentialData = attestedCredentialData;
    this.signCount = signCount;
  }

  public WebAuthn4jCredential(
      String id,
      String userId,
      String username,
      String userDisplayName,
      String rpId,
      String aaguid,
      String attestedCredentialData,
      Integer signatureAlgorithm,
      long signCount,
      Boolean rk,
      Boolean backupEligible,
      Boolean backupState,
      Map<String, Object> authenticator,
      Map<String, Object> attestation,
      Map<String, Object> extensions,
      Map<String, Object> device,
      Map<String, Object> metadata,
      Long createdAt,
      Long updatedAt,
      Long authenticatedAt) {
    this.id = id;
    this.userId = userId;
    this.username = username;
    this.userDisplayName = userDisplayName;
    this.rpId = rpId;
    this.aaguid = aaguid;
    this.attestedCredentialData = attestedCredentialData;
    this.signatureAlgorithm = signatureAlgorithm;
    this.signCount = signCount;
    this.rk = rk;
    this.backupEligible = backupEligible;
    this.backupState = backupState;
    this.authenticator = authenticator != null ? authenticator : new HashMap<>();
    this.attestation = attestation != null ? attestation : new HashMap<>();
    this.extensions = extensions != null ? extensions : new HashMap<>();
    this.device = device != null ? device : new HashMap<>();
    this.metadata = metadata != null ? metadata : new HashMap<>();
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
    this.authenticatedAt = authenticatedAt;
  }

  public String id() {
    return id;
  }

  public byte[] idAsBytes() {
    return urlDecoder.decode(id);
  }

  public String userId() {
    return userId;
  }

  public String username() {
    return username;
  }

  public String userDisplayName() {
    return userDisplayName;
  }

  public String rpId() {
    return rpId;
  }

  public String aaguid() {
    return aaguid;
  }

  public String attestedCredentialData() {
    return attestedCredentialData;
  }

  public byte[] attestedCredentialDataAsBytes() {
    return urlDecoder.decode(attestedCredentialData);
  }

  public Integer signatureAlgorithm() {
    return signatureAlgorithm;
  }

  public long signCount() {
    return signCount;
  }

  public Boolean rk() {
    return rk;
  }

  public Boolean backupEligible() {
    return backupEligible;
  }

  public Boolean backupState() {
    return backupState;
  }

  public Map<String, Object> authenticator() {
    return authenticator;
  }

  public Map<String, Object> attestation() {
    return attestation;
  }

  public Map<String, Object> extensions() {
    return extensions;
  }

  public Map<String, Object> device() {
    return device;
  }

  public Map<String, Object> metadata() {
    return metadata;
  }

  // Convenience methods for commonly accessed fields from JSON

  @SuppressWarnings("unchecked")
  public List<String> transports() {
    Object transports = authenticator.get("transports");
    if (transports instanceof List) {
      return (List<String>) transports;
    }
    return List.of();
  }

  public String attestationType() {
    Object type = attestation.get("type");
    return type != null ? type.toString() : null;
  }

  public Integer credProtect() {
    Object credProtect = extensions.get("cred_protect");
    if (credProtect instanceof Number) {
      return ((Number) credProtect).intValue();
    }
    return null;
  }

  public Long createdAt() {
    return createdAt;
  }

  public Long updatedAt() {
    return updatedAt;
  }

  public Long authenticatedAt() {
    return authenticatedAt;
  }

  public Map<String, Object> toMap() {
    Map<String, Object> result = new HashMap<>();
    result.put("id", id);
    result.put("user_id", userId);
    result.put("username", username);
    result.put("user_display_name", userDisplayName);
    result.put("rp_id", rpId);
    result.put("aaguid", aaguid);
    result.put("attested_credential_data", attestedCredentialData);
    result.put("signature_algorithm", signatureAlgorithm);
    result.put("sign_count", signCount);
    result.put("rk", rk);
    result.put("backup_eligible", backupEligible);
    result.put("backup_state", backupState);
    result.put("authenticator", authenticator);
    result.put("attestation", attestation);
    result.put("extensions", extensions);
    result.put("device", device);
    result.put("metadata", metadata);
    result.put("created_at", createdAt);
    result.put("updated_at", updatedAt);
    result.put("authenticated_at", authenticatedAt);
    return result;
  }
}
