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

package org.idp.server.core.openid.token.service;

import java.net.URI;
import java.net.http.HttpRequest;
import org.idp.server.core.openid.oauth.configuration.client.AvailableFederation;
import org.idp.server.core.openid.token.exception.TokenBadRequestException;
import org.idp.server.platform.http.HttpRequestExecutor;
import org.idp.server.platform.http.HttpRequestResult;
import org.idp.server.platform.jose.JsonWebSignature;
import org.idp.server.platform.jose.JwtCredential;
import org.idp.server.platform.jose.JwtSignatureVerifier;

/**
 * FederationJwtVerifier
 *
 * <p>Shared verifier for external IdP JWT signature verification. Used by both JWT Bearer Grant
 * (RFC 7523) and Token Exchange (RFC 8693).
 *
 * <p>RFC 8693 Section 2.1:
 *
 * <blockquote>
 *
 * The authorization server MUST perform the appropriate validation procedures for the indicated
 * token type.
 *
 * </blockquote>
 *
 * <p>For JWT-type subject_tokens, this means verifying the JWS signature against the external IdP's
 * JWKS (resolved from inline {@code jwks} or fetched from {@code jwks_uri}).
 *
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc8693#section-2.1">RFC 8693 Section 2.1</a>
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc7523">RFC 7523 - JWT Bearer</a>
 */
public class FederationJwtVerifier {

  HttpRequestExecutor httpRequestExecutor;

  public FederationJwtVerifier(HttpRequestExecutor httpRequestExecutor) {
    this.httpRequestExecutor = httpRequestExecutor;
  }

  /**
   * Verifies the JWS signature of a subject_token against the external IdP's JWKS.
   *
   * @param jws the parsed JSON Web Signature
   * @param federation the federation configuration containing jwks or jwks_uri
   * @throws TokenBadRequestException with {@code invalid_grant} if signature verification fails or
   *     JWKS is not configured
   */
  public void verifyExternalIdpSignature(JsonWebSignature jws, AvailableFederation federation) {
    try {
      String jwks = resolveJwks(federation);

      JwtCredential jwtCredential = JwtCredential.asymmetric(jwks);
      JwtSignatureVerifier signatureVerifier = new JwtSignatureVerifier();
      signatureVerifier.verify(jws, jwtCredential);
    } catch (TokenBadRequestException e) {
      throw e;
    } catch (Exception e) {
      throw new TokenBadRequestException(
          "invalid_grant", "External IdP signature verification failed: " + e.getMessage());
    }
  }

  String resolveJwks(AvailableFederation federation) {
    if (federation.hasJwks()) {
      return federation.jwks();
    }
    if (federation.hasJwksUri()) {
      return fetchJwks(federation.jwksUri());
    }
    throw new TokenBadRequestException(
        "invalid_grant",
        String.format(
            "Federation '%s' does not have JWKS configured (neither jwks nor jwks_uri)",
            federation.issuer()));
  }

  String fetchJwks(String jwksUri) {
    try {
      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(URI.create(jwksUri))
              .GET()
              .header("Accept", "application/json")
              .build();

      HttpRequestResult result = httpRequestExecutor.execute(request);

      if (result.isClientError() || result.isServerError()) {
        throw new TokenBadRequestException(
            "invalid_grant",
            String.format("Failed to fetch JWKS from '%s': HTTP %d", jwksUri, result.statusCode()));
      }

      return result.body().toString();
    } catch (TokenBadRequestException e) {
      throw e;
    } catch (Exception e) {
      throw new TokenBadRequestException(
          "invalid_grant",
          String.format("Failed to fetch JWKS from '%s': %s", jwksUri, e.getMessage()));
    }
  }
}
