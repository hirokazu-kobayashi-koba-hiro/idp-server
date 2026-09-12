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

package org.idp.server.core.openid.token;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.idp.server.core.openid.oauth.clientattestation.ClientAttestationJwt;
import org.idp.server.core.openid.oauth.clientattestation.ClientAttestationPopJwt;
import org.idp.server.core.openid.oauth.type.mtls.ClientCert;
import org.idp.server.core.openid.oauth.type.oauth.ClientSecretBasic;
import org.idp.server.core.openid.token.handler.tokenintrospection.io.TokenIntrospectionExtensionRequest;
import org.idp.server.core.openid.token.handler.tokenintrospection.io.TokenIntrospectionRequest;
import org.idp.server.core.openid.token.handler.tokenrevocation.io.TokenRevocationRequest;
import org.idp.server.core.openid.token.tokenintrospection.TokenIntrospectionRequestContext;
import org.idp.server.core.openid.token.tokenintrospection.TokenIntrospectionRequestParameters;
import org.idp.server.core.openid.token.tokenrevocation.TokenRevocationRequestContext;
import org.idp.server.core.openid.token.tokenrevocation.TokenRevocationRequestParameters;
import org.idp.server.platform.http.HttpRequestInputs;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.junit.jupiter.api.Test;

/**
 * Issue #1521: the introspection and revocation endpoints used to leave {@code
 * clientAttestationJwt()} / {@code clientAttestationPopJwt()} at the empty default of {@link
 * org.idp.server.core.openid.oauth.clientauthenticator.BackchannelRequestContext}, so a client
 * configured with {@code attest_jwt_client_auth} always failed authentication there.
 */
class ClientAttestationEndpointWiringTest {

  private static final String ATTESTATION = "attestation.jwt.value";
  private static final String POP = "pop.jwt.value";

  private static HttpRequestInputs inputsWithAttestationHeaders() {
    // Header names are matched case-insensitively (RFC 9110 Section 5.1), so the lower-cased form
    // a servlet container hands over must resolve just as well as the spec's canonical casing.
    return new HttpRequestInputs(
        null,
        Map.of(),
        Map.of(
            "oauth-client-attestation", List.of(ATTESTATION),
            "oauth-client-attestation-pop", List.of(POP)),
        null,
        "POST",
        "https://as.example.com/tokens/introspection");
  }

  private static HttpRequestInputs inputsWithoutHeaders() {
    return new HttpRequestInputs(null, Map.of(), Map.of(), null, "POST", "https://as.example.com");
  }

  @Test
  void introspectionRequestReadsAttestationHeaders() {
    TokenIntrospectionRequest request =
        new TokenIntrospectionRequest(new Tenant(), inputsWithAttestationHeaders());

    assertEquals(ATTESTATION, request.toClientAttestationJwt().value());
    assertEquals(POP, request.toClientAttestationPopJwt().value());
  }

  @Test
  void introspectionExtensionRequestReadsAttestationHeaders() {
    // The introspection-extensions endpoint authenticates the Resource Server itself, so its
    // Client Attestation is read from the headers rather than forwarded in the body.
    TokenIntrospectionExtensionRequest request =
        new TokenIntrospectionExtensionRequest(new Tenant(), inputsWithAttestationHeaders());

    assertEquals(ATTESTATION, request.toClientAttestationJwt().value());
    assertEquals(POP, request.toClientAttestationPopJwt().value());
  }

  @Test
  void revocationRequestReadsAttestationHeaders() {
    TokenRevocationRequest request =
        new TokenRevocationRequest(new Tenant(), inputsWithAttestationHeaders());

    assertEquals(ATTESTATION, request.toClientAttestationJwt().value());
    assertEquals(POP, request.toClientAttestationPopJwt().value());
  }

  @Test
  void absentHeadersProduceEmptyValues() {
    TokenIntrospectionRequest introspection =
        new TokenIntrospectionRequest(new Tenant(), inputsWithoutHeaders());
    TokenRevocationRequest revocation =
        new TokenRevocationRequest(new Tenant(), inputsWithoutHeaders());

    assertFalse(introspection.toClientAttestationJwt().exists());
    assertFalse(introspection.toClientAttestationPopJwt().exists());
    assertFalse(revocation.toClientAttestationJwt().exists());
    assertFalse(revocation.toClientAttestationPopJwt().exists());
  }

  @Test
  void repeatedHeadersArePreservedSoTheValidatorCanRejectThem() {
    // draft-ietf-oauth-attestation-based-client-auth-10 Section 7.1 / 7.2 require precisely one
    // header field each; ClientAttestationHeaderValidator needs every value to detect a violation.
    HttpRequestInputs inputs =
        new HttpRequestInputs(
            null,
            Map.of(),
            Map.of(
                "oauth-client-attestation", List.of(ATTESTATION, "second.attestation"),
                "oauth-client-attestation-pop", List.of(POP, "second.pop")),
            null,
            "POST",
            "https://as.example.com");

    TokenIntrospectionRequest request = new TokenIntrospectionRequest(new Tenant(), inputs);

    assertEquals(2, request.clientAttestationHeaders().size());
    assertEquals(2, request.clientAttestationPopHeaders().size());
  }

  @Test
  void introspectionContextExposesTenantAndAttestationInsteadOfTheEmptyDefault() {
    Tenant tenant = new Tenant();
    TokenIntrospectionRequestContext context =
        new TokenIntrospectionRequestContext(
            tenant,
            new ClientSecretBasic(),
            new ClientCert(),
            new ClientAttestationJwt(ATTESTATION),
            new ClientAttestationPopJwt(POP),
            new TokenIntrospectionRequestParameters(),
            null,
            null);

    assertSame(tenant, context.tenant());
    assertEquals(ATTESTATION, context.clientAttestationJwt().value());
    assertEquals(POP, context.clientAttestationPopJwt().value());
  }

  @Test
  void revocationContextExposesTenantAndAttestationInsteadOfTheEmptyDefault() {
    Tenant tenant = new Tenant();
    TokenRevocationRequestContext context =
        new TokenRevocationRequestContext(
            tenant,
            new ClientSecretBasic(),
            new ClientCert(),
            new ClientAttestationJwt(ATTESTATION),
            new ClientAttestationPopJwt(POP),
            new TokenRevocationRequestParameters(),
            null,
            null);

    assertSame(tenant, context.tenant());
    assertEquals(ATTESTATION, context.clientAttestationJwt().value());
    assertEquals(POP, context.clientAttestationPopJwt().value());
  }
}
