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

package org.idp.server.core.extension.verifiable_credentials.handler.io;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.extension.verifiable_credentials.BatchVerifiableCredentialResponses;
import org.idp.server.core.extension.verifiable_credentials.VerifiableCredentialErrorResponse;

public class BatchCredentialResponse {
  CredentialRequestStatus status;
  BatchVerifiableCredentialResponses responses;
  VerifiableCredentialErrorResponse errorResponse;
  Map<String, String> headers;

  public BatchCredentialResponse(
      CredentialRequestStatus status, BatchVerifiableCredentialResponses responses) {
    this.status = status;
    this.responses = responses;
    this.errorResponse = new VerifiableCredentialErrorResponse();
    Map<String, String> values = new HashMap<>();
    values.put("Content-Typ", "application/json");
    values.put("Cache-Control", "no-store");
    values.put("Pragma", "no-cache");
    this.headers = values;
  }

  public BatchCredentialResponse(
      CredentialRequestStatus status, VerifiableCredentialErrorResponse errorResponse) {
    this.status = status;
    this.responses = new BatchVerifiableCredentialResponses();
    this.errorResponse = errorResponse;
    Map<String, String> values = new HashMap<>();
    values.put("Content-Typ", "application/json");
    values.put("Cache-Control", "no-store");
    values.put("Pragma", "no-cache");
    this.headers = values;
  }

  public String contents() {
    if (status.isOK()) {
      return responses.contents();
    }
    return errorResponse.contents();
  }

  public int statusCode() {
    return status.statusCode();
  }

  public Map<String, String> headers() {
    return headers;
  }
}
