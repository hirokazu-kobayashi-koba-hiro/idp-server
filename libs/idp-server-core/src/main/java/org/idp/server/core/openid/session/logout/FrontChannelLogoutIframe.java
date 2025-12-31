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

package org.idp.server.core.openid.session.logout;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.idp.server.core.openid.session.ClientSessionIdentifier;

public class FrontChannelLogoutIframe {

  private final String clientId;
  private final String logoutUri;
  private final String issuer;
  private final String sid;

  public FrontChannelLogoutIframe(String clientId, String logoutUri, String issuer, String sid) {
    this.clientId = clientId;
    this.logoutUri = logoutUri;
    this.issuer = issuer;
    this.sid = sid;
  }

  public static FrontChannelLogoutIframe create(
      String clientId,
      String logoutUri,
      String issuer,
      ClientSessionIdentifier sid,
      boolean sessionRequired) {
    String sidValue = null;
    if (sessionRequired && sid != null) {
      sidValue = sid.value();
    }
    return new FrontChannelLogoutIframe(clientId, logoutUri, issuer, sidValue);
  }

  public String clientId() {
    return clientId;
  }

  public String logoutUri() {
    return logoutUri;
  }

  public String issuer() {
    return issuer;
  }

  public String sid() {
    return sid;
  }

  public String buildUrl() {
    StringBuilder sb = new StringBuilder(logoutUri);
    String separator = logoutUri.contains("?") ? "&" : "?";

    sb.append(separator).append("iss=").append(urlEncode(issuer));
    if (sid != null && !sid.isEmpty()) {
      sb.append("&sid=").append(urlEncode(sid));
    }

    return sb.toString();
  }

  public String toHtml() {
    return String.format(
        "<iframe src=\"%s\" style=\"display:none\" width=\"0\" height=\"0\"></iframe>", buildUrl());
  }

  private String urlEncode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }
}
