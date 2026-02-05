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

import java.io.Serializable;
import java.util.List;
import org.idp.server.platform.json.JsonReadable;

/**
 * Represents the persisted context for FIDO2 challenge operations.
 *
 * <p>This class encapsulates the challenge and associated user information that must be stored
 * during the FIDO2 registration flow. The context is created during challenge generation and
 * retrieved during credential verification to ensure the response corresponds to the original
 * challenge and user.
 *
 * <p><b>Security Consideration:</b> This context is stored server-side and retrieved using the
 * authentication transaction identifier. It prevents replay attacks and ensures the credential is
 * associated with the correct user.
 *
 * <p><b>Usage Flow:</b>
 *
 * <ol>
 *   <li>Challenge Generation: Create context with challenge and user info, persist to transaction
 *       store
 *   <li>Credential Creation: Client creates credential using challenge
 *   <li>Verification: Retrieve context, validate challenge matches, associate credential with user
 * </ol>
 *
 * <p><b>Usage Example:</b>
 *
 * <pre>{@code
 * // During challenge generation
 * WebAuthn4jChallenge challenge = new WebAuthn4jChallenge(challengeString);
 * WebAuthn4jUser user = new WebAuthn4jUser(userId, username, displayName);
 * WebAuthn4jChallengeContext context = new WebAuthn4jChallengeContext(challenge, user);
 *
 * transactionRepository.register(tenant, txnId, "webauthn4j", context);
 *
 * // During verification
 * WebAuthn4jChallengeContext retrievedContext =
 *     transactionRepository.get(tenant, txnId, "webauthn4j", WebAuthn4jChallengeContext.class);
 * WebAuthn4jUser expectedUser = retrievedContext.user();
 * }</pre>
 *
 * @see WebAuthn4jChallenge
 * @see WebAuthn4jUser
 */
public class WebAuthn4jChallengeContext implements Serializable, JsonReadable {

  /** The challenge generated for this FIDO2 operation. */
  WebAuthn4jChallenge challenge;

  /** The user information associated with this challenge. */
  WebAuthn4jUser user;

  /**
   * The allowed credential IDs for Non-Discoverable Credential authentication. Null for
   * Discoverable Credential (Passkey) authentication.
   */
  List<String> allowCredentialIds;

  /** Default constructor for JSON deserialization. */
  public WebAuthn4jChallengeContext() {}

  /**
   * Constructs a WebAuthn4jChallengeContext with the specified challenge and user.
   *
   * @param challenge the FIDO2 challenge
   * @param user the user information
   */
  public WebAuthn4jChallengeContext(WebAuthn4jChallenge challenge, WebAuthn4jUser user) {
    this.challenge = challenge;
    this.user = user;
    this.allowCredentialIds = null;
  }

  /**
   * Constructs a WebAuthn4jChallengeContext with allowed credential IDs for Non-Discoverable
   * Credential authentication.
   *
   * @param challenge the FIDO2 challenge
   * @param user the user information
   * @param allowCredentialIds the list of allowed credential IDs (Base64URL encoded)
   */
  public WebAuthn4jChallengeContext(
      WebAuthn4jChallenge challenge, WebAuthn4jUser user, List<String> allowCredentialIds) {
    this.challenge = challenge;
    this.user = user;
    this.allowCredentialIds = allowCredentialIds;
  }

  /**
   * Returns the challenge.
   *
   * @return the FIDO2 challenge
   */
  public WebAuthn4jChallenge challenge() {
    return challenge;
  }

  /**
   * Returns the user information.
   *
   * @return the FIDO2 user
   */
  public WebAuthn4jUser user() {
    return user;
  }

  /**
   * Returns the allowed credential IDs for Non-Discoverable Credential authentication.
   *
   * @return the list of allowed credential IDs, or null for Discoverable Credential authentication
   */
  public List<String> allowCredentialIds() {
    return allowCredentialIds;
  }

  /**
   * Checks if this context has allowed credential IDs (Non-Discoverable Credential flow).
   *
   * @return true if allowCredentialIds is set and not empty
   */
  public boolean hasAllowCredentialIds() {
    return allowCredentialIds != null && !allowCredentialIds.isEmpty();
  }
}
