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

package org.idp.server.federation.sso.oidc;

/**
 * Outcome of a federation userinfo execution.
 *
 * <p><b>Issue #1800:</b> carries the resolved status rather than only its class. The three-value
 * form could not express a {@code response_resolve_configs} mapping to 429 or 503, so an upstream
 * IdP being rate limited or in planned maintenance arrived as a flat 400 or 500 — the caller could
 * not tell "the request was wrong" from "come back later". Mirrors {@code
 * AuthenticationExecutionStatus}, which #1783 established on the authentication side.
 */
public enum UserinfoExecutionStatus {
  OK(200),
  CLIENT_ERROR(400),
  UNAUTHORIZED(401),
  FORBIDDEN(403),
  NOT_FOUND(404),
  REQUEST_TIMEOUT(408),
  CONFLICT(409),
  TOO_MANY_REQUESTS(429),
  SERVER_ERROR(500),
  BAD_GATEWAY(502),
  SERVICE_UNAVAILABLE(503),
  GATEWAY_TIMEOUT(504);

  int statusCode;

  UserinfoExecutionStatus(int statusCode) {
    this.statusCode = statusCode;
  }

  public boolean isOk() {
    return this == OK;
  }

  /**
   * Classifies by status code range, not by enum identity.
   *
   * <p>Identity comparison was correct only while the enum held exactly one 4xx and one 5xx. With
   * the codes above it would answer false for {@code UNAUTHORIZED} and {@code TOO_MANY_REQUESTS},
   * and {@link #isError()} deciding a 429 is not an error would let a failed userinfo request be
   * treated as a successful one.
   */
  public boolean isClientError() {
    return statusCode >= 400 && statusCode < 500;
  }

  public boolean isServerError() {
    return statusCode >= 500;
  }

  public boolean isError() {
    return isClientError() || isServerError();
  }

  public int code() {
    return statusCode;
  }

  /**
   * Maps an HTTP status code onto this enum.
   *
   * <p>Codes with no constant fall back to their class, so an unlisted 4xx / 5xx is still reported
   * as a failure rather than throwing. Any 2xx collapses to {@code OK}, matching {@link
   * org.idp.server.core.openid.federation.FederationInteractionStatus} — without it a 204 would
   * become {@code SERVER_ERROR}, which is the wrong default for a value this permissive to produce.
   */
  public static UserinfoExecutionStatus fromStatusCode(int statusCode) {
    if (statusCode >= 200 && statusCode <= 299) {
      return OK;
    }
    for (UserinfoExecutionStatus status : values()) {
      if (status.statusCode == statusCode) {
        return status;
      }
    }
    if (statusCode >= 400 && statusCode < 500) {
      return CLIENT_ERROR;
    }
    return SERVER_ERROR;
  }
}
