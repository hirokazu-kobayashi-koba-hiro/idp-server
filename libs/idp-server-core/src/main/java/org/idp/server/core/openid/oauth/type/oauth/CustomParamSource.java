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

package org.idp.server.core.openid.oauth.type.oauth;

/**
 * Where a custom parameter of an authorization request came from (Issue #1907). It tells whether
 * the end-user could have changed the value on the way: a value from the query string could have
 * been, one from a signed request object or from a pushed authorization request could not.
 */
public enum CustomParamSource {

  /** The query string of the authorization request. Not protected; the end-user can change it. */
  QUERY("query"),

  /** A claim of the signed request object (RFC 9101). Protected by the client's signature. */
  REQUEST_OBJECT("request_object"),

  /** The body of a pushed authorization request (RFC 9126). Protected by client authentication. */
  PUSHED("pushed");

  String value;

  CustomParamSource(String value) {
    this.value = value;
  }

  /**
   * @return the source named {@code value}, or null when there is none by that name
   */
  public static CustomParamSource of(String value) {
    for (CustomParamSource source : values()) {
      if (source.value.equals(value)) {
        return source;
      }
    }
    return null;
  }

  public String value() {
    return value;
  }
}
