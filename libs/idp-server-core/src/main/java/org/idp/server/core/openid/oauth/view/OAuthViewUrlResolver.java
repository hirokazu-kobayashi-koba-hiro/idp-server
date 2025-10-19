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

package org.idp.server.core.openid.oauth.view;

import org.idp.server.core.openid.oauth.OAuthRequestContext;
import org.idp.server.core.openid.oauth.type.oauth.CustomParams;
import org.idp.server.core.openid.oauth.type.oauth.Error;
import org.idp.server.core.openid.oauth.type.oauth.ErrorDescription;
import org.idp.server.platform.http.HttpQueryParams;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.config.UIConfiguration;

public class OAuthViewUrlResolver {

  public static String resolve(OAuthRequestContext context) {
    Tenant tenant = context.tenant();
    UIConfiguration uiConfiguration = tenant.uiConfiguration();
    String base = tenant.baseUrl();

    if (context.isPromptCreate()) {
      String signupPage = uiConfiguration.signupPage();
      return buildUrl(base, signupPage, context);
    }

    String signinPage = uiConfiguration.signinPage();
    return buildUrl(base, signinPage, context);
  }

  public static String resolveError(Tenant tenant, Error error, ErrorDescription errorDescription) {
    String base = tenant.baseUrl();
    return String.format(
        "%s/error?error=%s&error_description=%s&tenant_id=%s",
        base, error.value(), errorDescription.value(), tenant.identifier().value());
  }

  private static String buildUrl(String base, String path, OAuthRequestContext context) {
    String normalizedBase = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    String normalizedPath = path.startsWith("/") ? path.replaceFirst("/", "") : path;
    CustomParams customParams = context.authorizationRequest().customParams();
    HttpQueryParams httpQueryParams = new HttpQueryParams(customParams.values());
    httpQueryParams.add("id", context.authorizationRequestIdentifier().value());
    httpQueryParams.add("tenant_id", context.tenantIdentifier().value());
    String params = httpQueryParams.params();
    return String.format("%s/%s?%s", normalizedBase, normalizedPath, params);
  }
}
