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

package org.idp.server.core.openid.oauth.type.extension;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Claim names the end-user declined to share on the consent screen.
 *
 * <p>The consent UI presents the requested claims from the authorization view-data; the names the
 * user unselects are returned here. At grant build time they are removed from the granted id_token
 * / userinfo / verified_claims so the issued tokens omit non-consented data (OIDC4IDA Section
 * 5.7.3). Mirrors {@link DeniedScopes}.
 */
public class DeniedClaims implements Iterable<String> {

  List<String> values;

  public DeniedClaims() {
    this.values = new ArrayList<>();
  }

  public DeniedClaims(List<String> values) {
    this.values = values != null ? values : new ArrayList<>();
  }

  public boolean contains(String claimName) {
    return values.contains(claimName);
  }

  public boolean isEmpty() {
    return values.isEmpty();
  }

  public List<String> toList() {
    return values;
  }

  @Override
  public Iterator<String> iterator() {
    return values.iterator();
  }
}
