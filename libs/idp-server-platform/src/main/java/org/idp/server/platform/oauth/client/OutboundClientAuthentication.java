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
package org.idp.server.platform.oauth.client;

import java.net.http.HttpRequest;
import java.util.HashMap;
import java.util.Map;

/** What a client authentication adds to a token request: headers, and form parameters. */
public class OutboundClientAuthentication {

  Map<String, String> headers;
  Map<String, String> formParameters;

  public OutboundClientAuthentication(
      Map<String, String> headers, Map<String, String> formParameters) {
    this.headers = headers;
    this.formParameters = formParameters;
  }

  public Map<String, String> headers() {
    return headers;
  }

  public Map<String, String> formParameters() {
    return formParameters;
  }

  /** {@code parameters} with the authentication's form parameters added. */
  public Map<String, String> addTo(Map<String, String> parameters) {
    Map<String, String> merged = new HashMap<>(parameters);
    merged.putAll(formParameters);
    return merged;
  }

  public void applyTo(HttpRequest.Builder builder) {
    headers.forEach(builder::header);
  }
}
