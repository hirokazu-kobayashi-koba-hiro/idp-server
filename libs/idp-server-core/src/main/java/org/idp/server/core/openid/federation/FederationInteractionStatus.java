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

package org.idp.server.core.openid.federation;

/**
 * Outcome of a federation interaction, as returned to the caller of the callback endpoint.
 *
 * <p><b>Issue #1800:</b> carries the status the upstream actually answered. The three-value form
 * flattened every failure to 400 or 500, so an IdP that was rate limiting (429) or briefly
 * unavailable (503) was indistinguishable from a malformed request — the client could not tell
 * whether retrying made sense. This is the second of two places that flattened it; {@code
 * UserinfoExecutionStatus} was the first, and fixing only that one would have changed nothing
 * observable because the status was re-flattened here.
 *
 * <p>Applies to every upstream failure surfaced through the callback, not only userinfo: the token
 * request, the JWKS fetch and ID token verification all report through this type.
 */
public enum FederationInteractionStatus {
  SUCCESS(200),
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

  FederationInteractionStatus(int statusCode) {
    this.statusCode = statusCode;
  }

  public int statusCode() {
    return statusCode;
  }

  public boolean isSuccess() {
    return this == SUCCESS;
  }

  /**
   * Classifies by status code range, not by enum identity.
   *
   * <p>Identity comparison was correct only while the enum held exactly one 4xx and one 5xx. With
   * the codes above it would answer false for {@code TOO_MANY_REQUESTS}, and the caller branches on
   * {@code isError()} alone — a 429 read as "not an error" would let the callback continue into
   * authorization with no user resolved.
   */
  public boolean isError() {
    return statusCode >= 400;
  }

  /**
   * Maps an HTTP status code onto this enum.
   *
   * <p>Codes with no constant fall back to their class, so an unlisted 4xx / 5xx is still reported
   * as a failure rather than throwing. 2xx other than 200 collapses to {@code SUCCESS}, which is
   * the pre-existing behaviour.
   */
  public static FederationInteractionStatus fromStatusCode(int statusCode) {
    if (statusCode >= 200 && statusCode <= 299) {
      return SUCCESS;
    }
    for (FederationInteractionStatus status : values()) {
      if (status.statusCode == statusCode) {
        return status;
      }
    }
    if (statusCode >= 400 && statusCode <= 499) {
      return CLIENT_ERROR;
    }
    return SERVER_ERROR;
  }
}
