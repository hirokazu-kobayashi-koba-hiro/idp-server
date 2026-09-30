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

import org.idp.server.core.openid.oauth.type.oauth.Error;
import org.idp.server.core.openid.oauth.type.oauth.ErrorDescription;
import org.idp.server.core.openid.oauth.view.OAuthViewUrlResolver;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

/**
 * What the browser gets from the first-party hand-off at the end of an authorization flow.
 *
 * <p>Always a redirect: this is a top level navigation, so whatever it answers is what the end-user
 * sees. On success, the client's redirect. On failure, the tenant's error page, as for an
 * authorization request that cannot be processed — never the client, because the redirect this
 * hand-off would have trusted is exactly what could not be established, and never a raw body the
 * end-user would be left looking at.
 */
public class OAuthCompleteResponse {

  String location;

  private OAuthCompleteResponse(String location) {
    this.location = location;
  }

  public static OAuthCompleteResponse redirect(String redirectUri) {
    return new OAuthCompleteResponse(redirectUri);
  }

  public static OAuthCompleteResponse errorPage(Tenant tenant, String error, String description) {
    return new OAuthCompleteResponse(
        OAuthViewUrlResolver.resolveError(
            tenant, new Error(error), new ErrorDescription(description)));
  }

  public String location() {
    return location;
  }
}
