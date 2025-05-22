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


package org.idp.server.basic.type.oidc.logout;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;

/**
 * post_logout_redirect_uri OPTIONAL
 *
 * <p>URI to which the RP is requesting that the End-User's User Agent be redirected after a logout
 * has been performed. This URI SHOULD use the https scheme and MAY contain port, path, and query
 * parameter components; however, it MAY use the http scheme, provided that the Client Type is
 * confidential, as defined in Section 2.1 of OAuth 2.0 [RFC6749], and provided the OP allows the
 * use of http RP URIs. The URI MAY use an alternate scheme, such as one that is intended to
 * identify a callback into a native application. The value MUST have been previously registered
 * with the OP, either using the post_logout_redirect_uris Registration parameter or via another
 * mechanism. An id_token_hint is also RECOMMENDED when this parameter is included. state
 *
 * @see <a href="https://openid.net/specs/openid-connect-rpinitiated-1_0.html#RPLogout">RP-Initiated
 *     Logout</a>
 */
public class PostLogoutRedirectUri {
  String value;

  public PostLogoutRedirectUri() {}

  public PostLogoutRedirectUri(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  public boolean exists() {
    return Objects.nonNull(value) && !value.isEmpty();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    PostLogoutRedirectUri that = (PostLogoutRedirectUri) o;
    return Objects.equals(value, that.value);
  }

  @Override
  public int hashCode() {
    return Objects.hash(value);
  }

  public URI toURI() throws URISyntaxException {
    return new URI(value);
  }
}
