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

package org.idp.server.core.openid.oauth.clientauthenticator.exception;

import org.idp.server.core.openid.oauth.type.oauth.RequestedClientId;

public class ClientUnAuthorizedException extends RuntimeException {
  private final String method;
  private final RequestedClientId clientId;
  private final String reason;

  public ClientUnAuthorizedException(String message) {
    super(message);
    this.method = null;
    this.clientId = null;
    this.reason = null;
  }

  public ClientUnAuthorizedException(String message, Throwable throwable) {
    super(message, throwable);
    this.method = null;
    this.clientId = null;
    this.reason = null;
  }

  public ClientUnAuthorizedException(String method, RequestedClientId clientId, String reason) {
    super(
        String.format(
            "Client authentication failed: method=%s, client_id=%s, reason=%s",
            method, clientId != null ? clientId.value() : "unknown", reason));
    this.method = method;
    this.clientId = clientId;
    this.reason = reason;
  }

  public ClientUnAuthorizedException(
      String method, RequestedClientId clientId, String reason, Throwable throwable) {
    super(
        String.format(
            "Client authentication failed: method=%s, client_id=%s, reason=%s",
            method, clientId != null ? clientId.value() : "unknown", reason),
        throwable);
    this.method = method;
    this.clientId = clientId;
    this.reason = reason;
  }

  public String getMethod() {
    return method;
  }

  public RequestedClientId getClientId() {
    return clientId;
  }

  public String getReason() {
    return reason;
  }

  public boolean hasStructuredData() {
    return method != null && clientId != null && reason != null;
  }
}
