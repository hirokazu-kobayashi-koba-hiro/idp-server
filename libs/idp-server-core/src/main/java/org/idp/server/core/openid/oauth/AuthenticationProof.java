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

package org.idp.server.core.openid.oauth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.openid.authentication.AuthenticationTransactionAttributes;
import org.idp.server.core.openid.oauth.type.oauth.RedirectUri;
import org.idp.server.core.openid.oauth.type.oauth.Subject;
import org.idp.server.platform.crypto.AesCipher;
import org.idp.server.platform.crypto.EncryptedData;
import org.idp.server.platform.random.RandomStringGenerator;

/**
 * Proof that this browser is the one that authenticated.
 *
 * <p>Travels as {@code auth_proof}: handed to the browser that supplied the credentials, and
 * required back from it before the authorization code is minted or delivered.
 *
 * <h2>Why a value and not a cookie</h2>
 *
 * <p>Where the authorization view is on another site, every step between the authorization request
 * and the redirect back runs as third-party XHR, and this server's cookies are neither sent nor
 * kept. Nothing the browser holds in a cookie can be relied on, so what distinguishes one browser
 * from another has to be a value the view itself carries.
 *
 * <p>The browser binding cookie could not do this job even where it survives, because it answers
 * the wrong question. It says "is this the browser that <i>started</i> the request?", and in the
 * attack this defends against that is the attacker's browser: he opens a request, sends the view's
 * URL to a victim, the victim authenticates, and the attacker holds the only cookie. Acting on it
 * hands him a session as the victim.
 *
 * <p>The question that has to be answered is "is this the browser that <i>authenticated</i>?", and
 * only one thing separates the two browsers there — the victim supplied credentials and the
 * attacker cannot. So this value is issued by the step where that happened, handed back in that
 * response, and required from then on.
 *
 * <p>Issued per step rather than when the whole transaction succeeds, because the two are often not
 * the same caller: a flow that finishes out of band is completed by the device, and a value handed
 * back there never reaches the browser that has to call {@code /authorize}.
 *
 * <h2>Rotation</h2>
 *
 * <p>It is issued twice, and each one is consumed on use:
 *
 * <ol>
 *   <li>at authentication success, with no redirect yet — this is what {@code /authorize} requires,
 *       so an attacker who knows the request id cannot mint a code from it
 *   <li>at {@code /authorize}, carrying the redirect the code went into — this is what {@code
 *       /complete} requires, so the code can only be delivered to the same browser
 * </ol>
 *
 * <h2>Where it is kept</h2>
 *
 * <p>On the authentication transaction, in the database — the value's hash, never the value. The
 * steps that issue and spend a proof already hold that transaction under a row lock, which is what
 * makes spending it single-use. A cache would have been the obvious place, but the cache is allowed
 * to be absent; this is not, and a proof that silently could not be read would either lock every
 * user out or, worse, read as "no proof was ever issued".
 *
 * <p>The second stage carries the redirect, and the redirect carries the authorization code — and,
 * for a hybrid response type, tokens. It is stored encrypted, as tokens are elsewhere.
 */
public class AuthenticationProof {

  /** The name this travels under, in the authorize body and in the {@code /complete} query. */
  public static final String KEY = "auth_proof";

  static final String AUTHENTICATED_ATTRIBUTE = "auth_proof";
  static final String COMPLETION_ATTRIBUTE = "completion_proof";

  String value;
  String hash;
  Subject sub;
  RedirectUri redirectUri;

  private AuthenticationProof(String value, String hash, Subject sub, RedirectUri redirectUri) {
    this.value = value;
    this.hash = hash;
    this.sub = sub;
    this.redirectUri = redirectUri;
  }

  /** The first of the two: what {@code /authorize} asks for. */
  public static AuthenticationProof authenticated(Subject sub) {
    String value = new RandomStringGenerator(32).generate();
    return new AuthenticationProof(value, hashOf(value), sub, new RedirectUri());
  }

  /** The second of the two: what {@code /complete} asks for, carrying the redirect. */
  public static AuthenticationProof forCompletion(Subject sub, RedirectUri redirectUri) {
    String value = new RandomStringGenerator(32).generate();
    return new AuthenticationProof(value, hashOf(value), sub, redirectUri);
  }

  /** The first-stage proof stored on these attributes, or an empty one. */
  public static AuthenticationProof authenticatedOn(
      AuthenticationTransactionAttributes attributes) {
    return fromMap(attributes.getValueAsMap(AUTHENTICATED_ATTRIBUTE));
  }

  /** The second-stage proof stored on these attributes, with its redirect decrypted. */
  public static AuthenticationProof completionOn(
      AuthenticationTransactionAttributes attributes, AesCipher cipher) {
    return fromMap(attributes.getValueAsMap(COMPLETION_ATTRIBUTE), cipher);
  }

  /**
   * Stores this proof, replacing any earlier one of the same stage. Only the latest can be spent:
   * each browser step issues a new one, and the view keeps the latest.
   */
  public AuthenticationTransactionAttributes storeOn(
      AuthenticationTransactionAttributes attributes, AesCipher cipher) {
    return attributes.with(stageAttribute(), toMap(cipher));
  }

  /** Removes this stage's proof, which is what spending it means. */
  public AuthenticationTransactionAttributes spendOn(
      AuthenticationTransactionAttributes attributes) {
    return attributes.without(stageAttribute());
  }

  /** The value handed to the browser. Only a proof just issued has it; a stored one does not. */
  public String value() {
    return value;
  }

  public RedirectUri redirectUri() {
    return redirectUri;
  }

  public boolean exists() {
    return hash != null && !hash.isEmpty();
  }

  /**
   * Whether {@code presented} is this first-stage proof, earned by {@code sub}.
   *
   * <p>A second-stage proof never answers yes: it belongs to {@code /complete}, and accepting it
   * here would let a caller skip a step. The user is checked because a proof is issued per step,
   * and one earned before the transaction settled on a user must not carry over to whoever it
   * settled on.
   */
  public boolean authorizes(String presented, Subject sub) {
    return exists()
        && !hasRedirectUri()
        && this.sub.exists()
        && sub != null
        && this.sub.value().equals(sub.value())
        && matches(presented);
  }

  /** Whether {@code presented} is this second-stage proof. */
  public boolean completes(String presented) {
    return exists() && hasRedirectUri() && matches(presented);
  }

  public boolean hasRedirectUri() {
    return redirectUri.exists();
  }

  private boolean matches(String presented) {
    if (presented == null || presented.isEmpty()) {
      return false;
    }
    return MessageDigest.isEqual(
        hash.getBytes(StandardCharsets.UTF_8), hashOf(presented).getBytes(StandardCharsets.UTF_8));
  }

  private String stageAttribute() {
    return hasRedirectUri() ? COMPLETION_ATTRIBUTE : AUTHENTICATED_ATTRIBUTE;
  }

  private Map<String, Object> toMap(AesCipher cipher) {
    Map<String, Object> map = new HashMap<>();
    map.put("hash", hash);
    if (sub.exists()) map.put("sub", sub.value());
    if (hasRedirectUri()) map.put("redirect_uri", cipher.encrypt(redirectUri.value()).toMap());
    return map;
  }

  private static AuthenticationProof fromMap(Map<String, Object> map) {
    return new AuthenticationProof(
        null, (String) map.get("hash"), new Subject((String) map.get("sub")), new RedirectUri());
  }

  private static AuthenticationProof fromMap(Map<String, Object> map, AesCipher cipher) {
    RedirectUri redirectUri = new RedirectUri();
    if (map.get("redirect_uri") instanceof Map<?, ?> encrypted) {
      redirectUri =
          new RedirectUri(
              cipher.decrypt(
                  new EncryptedData(
                      (String) encrypted.get("cipher_text"), (String) encrypted.get("iv"))));
    }
    return new AuthenticationProof(
        null, (String) map.get("hash"), new Subject((String) map.get("sub")), redirectUri);
  }

  private static String hashOf(String value) {
    try {
      byte[] hashed =
          MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
      return Base64.getUrlEncoder().withoutPadding().encodeToString(hashed);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is required for auth_proof", e);
    }
  }
}
