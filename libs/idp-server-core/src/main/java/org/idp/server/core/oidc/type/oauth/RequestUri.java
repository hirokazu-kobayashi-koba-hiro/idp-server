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

package org.idp.server.core.oidc.type.oauth;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;

/** RequestUri */
public class RequestUri {
  String value;
  private static final String PUSHED_REQUEST_URI_PREFIX = "urn:ietf:params:oauth:request_uri:";

  public RequestUri() {}

  public RequestUri(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  public boolean exists() {
    return Objects.nonNull(value) && !value.isEmpty();
  }

  public static String createPushedRequestUri(String id) {
    return PUSHED_REQUEST_URI_PREFIX + id;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    RequestUri that = (RequestUri) o;
    return Objects.equals(value, that.value);
  }

  @Override
  public int hashCode() {
    return Objects.hash(value);
  }

  public URI toURI() throws URISyntaxException {
    return new URI(value);
  }

  public boolean isPushedRequestUri() {
    return value.startsWith(PUSHED_REQUEST_URI_PREFIX);
  }

  public String extractId() {
    return value.substring(PUSHED_REQUEST_URI_PREFIX.length());
  }
}
