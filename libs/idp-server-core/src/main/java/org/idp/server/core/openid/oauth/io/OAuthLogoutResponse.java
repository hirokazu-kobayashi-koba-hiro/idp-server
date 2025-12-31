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

package org.idp.server.core.openid.oauth.io;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.idp.server.core.openid.oauth.logout.OAuthLogoutContext;
import org.idp.server.core.openid.session.logout.FrontChannelLogoutIframe;

/**
 * OAuthLogoutResponse
 *
 * <p>OpenID Connect RP-Initiated Logout 1.0 Section 4: If the state parameter was included in the
 * logout request, it MUST be returned unmodified as a query parameter in the redirect URI.
 *
 * @see <a href="https://openid.net/specs/openid-connect-rpinitiated-1_0.html">RP-Initiated
 *     Logout</a>
 */
public class OAuthLogoutResponse {
  OAuthLogoutStatus status;
  String redirectUri;
  String error;
  String errorDescription;
  OAuthLogoutContext context;
  List<FrontChannelLogoutIframe> frontChannelIframes;

  public OAuthLogoutResponse() {
    this.frontChannelIframes = new ArrayList<>();
  }

  private OAuthLogoutResponse(
      OAuthLogoutStatus status,
      String redirectUri,
      String error,
      String errorDescription,
      OAuthLogoutContext context,
      List<FrontChannelLogoutIframe> frontChannelIframes) {
    this.status = status;
    this.redirectUri = redirectUri;
    this.error = error;
    this.errorDescription = errorDescription;
    this.context = context;
    this.frontChannelIframes =
        frontChannelIframes != null ? frontChannelIframes : new ArrayList<>();
  }

  public static OAuthLogoutResponse ok(OAuthLogoutContext context) {
    return new OAuthLogoutResponse(OAuthLogoutStatus.OK, "", "", "", context, null);
  }

  public static OAuthLogoutResponse redirect(String redirectUri, OAuthLogoutContext context) {
    return new OAuthLogoutResponse(
        OAuthLogoutStatus.REDIRECABLE_FOUND, redirectUri, "", "", context, null);
  }

  public static OAuthLogoutResponse badRequest(String error, String errorDescription) {
    return new OAuthLogoutResponse(
        OAuthLogoutStatus.BAD_REQUEST, "", error, errorDescription, null, null);
  }

  public static OAuthLogoutResponse serverError(String errorDescription) {
    return new OAuthLogoutResponse(
        OAuthLogoutStatus.SERVER_ERROR, "", "server_error", errorDescription, null, null);
  }

  public static OAuthLogoutResponse withFrontChannelIframes(
      OAuthLogoutResponse original, List<FrontChannelLogoutIframe> frontChannelIframes) {
    return new OAuthLogoutResponse(
        original.status,
        original.redirectUri,
        original.error,
        original.errorDescription,
        original.context,
        frontChannelIframes);
  }

  public Map<String, Object> contents() {
    if (status.isError()) {
      return Map.of("error", error, "error_description", errorDescription);
    }
    return Map.of("redirect_uri", redirectUri);
  }

  public OAuthLogoutStatus status() {
    return status;
  }

  public String redirectUri() {
    return redirectUri;
  }

  public boolean hasRedirectUri() {
    return redirectUri != null && !redirectUri.isEmpty();
  }

  public String error() {
    return error;
  }

  public String errorDescription() {
    return errorDescription;
  }

  public OAuthLogoutContext context() {
    return context;
  }

  public boolean hasContext() {
    return context != null;
  }

  public boolean isOk() {
    return status == OAuthLogoutStatus.OK || status == OAuthLogoutStatus.REDIRECABLE_FOUND;
  }

  public List<FrontChannelLogoutIframe> frontChannelIframes() {
    return frontChannelIframes;
  }

  public boolean hasFrontChannelIframes() {
    return frontChannelIframes != null && !frontChannelIframes.isEmpty();
  }
}
