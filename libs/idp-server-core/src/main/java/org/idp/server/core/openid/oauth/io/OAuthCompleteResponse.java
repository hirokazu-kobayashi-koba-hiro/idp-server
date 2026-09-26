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

import java.util.HashMap;
import java.util.Map;

/**
 * What the browser gets from the first-party hand-off at the end of an authorization flow.
 *
 * <p>Either a redirect to the client, or an error to show — never a body the authorization view is
 * expected to act on, because by this point the view is no longer driving: the browser has left it.
 */
public class OAuthCompleteResponse {

  String redirectUri;
  String error;
  String errorDescription;

  private OAuthCompleteResponse(String redirectUri, String error, String errorDescription) {
    this.redirectUri = redirectUri;
    this.error = error;
    this.errorDescription = errorDescription;
  }

  public static OAuthCompleteResponse redirect(String redirectUri) {
    return new OAuthCompleteResponse(redirectUri, null, null);
  }

  public static OAuthCompleteResponse error(String error, String errorDescription) {
    return new OAuthCompleteResponse(null, error, errorDescription);
  }

  public boolean isRedirect() {
    return redirectUri != null && !redirectUri.isEmpty();
  }

  public String redirectUri() {
    return redirectUri;
  }

  public Map<String, Object> contents() {
    Map<String, Object> contents = new HashMap<>();
    contents.put("error", error);
    contents.put("error_description", errorDescription);
    return contents;
  }
}
