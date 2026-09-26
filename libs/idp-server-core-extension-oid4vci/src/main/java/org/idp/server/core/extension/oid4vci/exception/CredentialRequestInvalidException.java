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
package org.idp.server.core.extension.oid4vci.exception;

/**
 * A Credential Request error of OpenID4VCI 1.0 Section 8.3.1.2, answered with HTTP 400 and the
 * given error code ({@code invalid_credential_request}, {@code unknown_credential_configuration},
 * {@code invalid_proof}, {@code invalid_nonce}, ...).
 */
public class CredentialRequestInvalidException extends RuntimeException {

  String error;

  public CredentialRequestInvalidException(String error, String errorDescription) {
    super(errorDescription);
    this.error = error;
  }

  public String error() {
    return error;
  }

  public String errorDescription() {
    return getMessage();
  }
}
