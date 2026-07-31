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

package org.idp.server.platform.http;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Bundles the HTTP-level input channels of a single request into one value object.
 *
 * <p>OAuth/OIDC endpoints (Token / PAR / Userinfo / Introspection / Revocation / CIBA) all consume
 * the same HTTP-derived inputs — body parameters, headers, the TLS-layer client certificate and the
 * HTTP method/URI. Historically each endpoint DTO re-declared these fields, so introducing a new
 * header-based spec (e.g. RFC 9449 DPoP, OAuth-Client-Attestation) required touching the
 * Controller, the API interface, the EntryService and every DTO. With this record the adapter layer
 * captures the full HTTP surface once, and a DTO exposes a new channel by adding a single accessor.
 *
 * <p>This record intentionally holds only {@code String} / {@code Map} values. Wrapping into
 * protocol value objects ({@code DPoPProof}, {@code ClientCert}, ...) is the consuming DTO's
 * responsibility, which keeps the platform → core dependency direction intact.
 *
 * <p>Header names are case-insensitive (RFC 9110 Section 5.1); keys of {@code headers} are
 * normalized to lower case and lookups accept any casing.
 *
 * @param authorizationHeader raw {@code Authorization} header value (Basic / Bearer / DPoP), {@code
 *     null} when absent
 * @param bodyParameters form-urlencoded body parameters, never {@code null} (normalized to an empty
 *     map)
 * @param headers all HTTP headers, keys lower-cased, repeated headers kept as multiple values
 * @param tlsClientCertPem PEM client certificate captured at the TLS layer (mTLS), {@code null}
 *     when absent
 * @param httpMethod HTTP method of the request (e.g. {@code "POST"}), referenced by DPoP {@code
 *     htm} verification
 * @param httpUri client-facing request URL, referenced by DPoP {@code htu} verification
 */
public record HttpRequestInputs(
    String authorizationHeader,
    Map<String, String[]> bodyParameters,
    Map<String, List<String>> headers,
    String tlsClientCertPem,
    String httpMethod,
    String httpUri) {

  public HttpRequestInputs {
    bodyParameters = bodyParameters == null ? Map.of() : bodyParameters;
    headers = headers == null ? Map.of() : normalizeHeaders(headers);
  }

  /**
   * Returns the first value of the given header.
   *
   * @param name header name, any casing
   */
  public Optional<String> firstHeader(String name) {
    List<String> values = headers.get(name.toLowerCase(Locale.ROOT));
    return values == null || values.isEmpty() ? Optional.empty() : Optional.of(values.get(0));
  }

  /**
   * Returns all values of the given header. Repeated headers (e.g. multiple DPoP headers, an RFC
   * 9449 Section 4.3 violation) are preserved so validators can detect them.
   *
   * @param name header name, any casing
   */
  public List<String> headerValues(String name) {
    return headers.getOrDefault(name.toLowerCase(Locale.ROOT), List.of());
  }

  public boolean hasHeader(String name) {
    return !headerValues(name).isEmpty();
  }

  private static Map<String, List<String>> normalizeHeaders(Map<String, List<String>> raw) {
    Map<String, List<String>> normalized = new LinkedHashMap<>(raw.size());
    for (Map.Entry<String, List<String>> entry : raw.entrySet()) {
      if (entry.getKey() == null) {
        continue;
      }
      List<String> values = entry.getValue();
      normalized.put(
          entry.getKey().toLowerCase(Locale.ROOT),
          values == null ? List.of() : Collections.unmodifiableList(values));
    }
    return Collections.unmodifiableMap(normalized);
  }
}
