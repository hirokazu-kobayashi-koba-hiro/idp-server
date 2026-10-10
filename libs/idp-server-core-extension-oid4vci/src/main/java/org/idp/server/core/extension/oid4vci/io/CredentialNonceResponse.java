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
package org.idp.server.core.extension.oid4vci.io;

import java.util.Map;
import org.idp.server.core.extension.oid4vci.nonce.CredentialNonce;

/**
 * Response of the Nonce Endpoint (Section 7.2). The {@code Cache-Control: no-store} header the
 * section requires is added by the adapter.
 */
public record CredentialNonceResponse(int statusCode, Map<String, Object> contents) {

  public static CredentialNonceResponse ok(CredentialNonce nonce) {
    return new CredentialNonceResponse(200, Map.of("c_nonce", nonce.value()));
  }

  /** The tenant issues no credential, so it has no Nonce Endpoint either. */
  public static CredentialNonceResponse notFound() {
    return new CredentialNonceResponse(
        404, Map.of("error", "not_found", "error_description", "this tenant issues no credential"));
  }

  /** The endpoint reads nothing from the request: any failure is this server's own. */
  public static CredentialNonceResponse serverError() {
    return new CredentialNonceResponse(500, Map.of("error", "server_error"));
  }
}
