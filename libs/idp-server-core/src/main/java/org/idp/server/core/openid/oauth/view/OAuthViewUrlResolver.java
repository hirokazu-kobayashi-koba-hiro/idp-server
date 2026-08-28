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
import org.idp.server.core.openid.oauth.request.AuthorizationRequest;
import org.idp.server.core.openid.oauth.type.oauth.CustomParams;
import org.idp.server.core.openid.oauth.type.oauth.Error;
import org.idp.server.core.openid.oauth.type.oauth.ErrorDescription;
import org.idp.server.platform.http.HttpQueryParams;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.config.UIConfiguration;
import org.idp.server.platform.multi_tenancy.tenant.config.UIViewVariant;

public class OAuthViewUrlResolver {

  public static String resolve(OAuthRequestContext context) {
    Tenant tenant = context.tenant();
    UIConfiguration uiConfiguration = tenant.uiConfiguration();
    UIViewVariant variant = resolveVariant(context, uiConfiguration);
    String base = variant.hasBaseUrl() ? variant.baseUrl() : tenant.baseUrl();

    if (context.isPromptCreate()) {
      String signupPage =
          variant.hasSignupPage() ? variant.signupPage() : uiConfiguration.signupPage();
      return buildUrl(base, signupPage, context);
    }

    String signinPage =
        variant.hasSigninPage() ? variant.signinPage() : uiConfiguration.signinPage();
    return buildUrl(base, signinPage, context);
  }

  /**
   * The pages this request should be sent to, when the tenant runs more than one set.
   *
   * <p>A canary release is driven by the relying party, which names the variant on the
   * authorization request; the tenant declares what each name resolves to. The name is used as a
   * key into that declaration and never as part of the path, because the authorization URL is
   * public and anyone can put a value on it. A name nobody declared resolves to an empty variant,
   * so the request lands on the tenant's default pages.
   *
   * <p>The name stays in the custom parameters, so it reaches the page on the URL and in view-data,
   * and it is stored with the authorization request.
   */
  private static UIViewVariant resolveVariant(
      OAuthRequestContext context, UIConfiguration uiConfiguration) {
    if (!uiConfiguration.hasVariants()) {
      return new UIViewVariant();
    }
    CustomParams customParams = context.authorizationRequest().customParams();
    return uiConfiguration.variant(
        customParams.getValueAsStringOrEmpty(uiConfiguration.variantParam()));
  }

  public static String resolveError(Tenant tenant, Error error, ErrorDescription errorDescription) {
    String base = tenant.baseUrl();
    return String.format(
        "%s/error/?error=%s&error_description=%s&tenant_id=%s",
        base, error.value(), errorDescription.value(), tenant.identifier().value());
  }

  private static String buildUrl(String base, String path, OAuthRequestContext context) {
    String normalizedBase = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    String normalizedPath = path.startsWith("/") ? path.replaceFirst("/", "") : path;
    AuthorizationRequest authorizationRequest = context.authorizationRequest();
    CustomParams customParams = authorizationRequest.customParams();
    HttpQueryParams httpQueryParams = new HttpQueryParams(customParams.values());
    httpQueryParams.add("id", context.authorizationRequestIdentifier().value());
    httpQueryParams.add("tenant_id", context.tenantIdentifier().value());
    // Carried on the URL as well as in view-data so the page can settle its language — html lang,
    // text direction — on first paint instead of after the view-data round trip. Kept in the
    // request's space-separated form; the array form is view-data's.
    if (authorizationRequest.hasUiLocales()) {
      httpQueryParams.add("ui_locales", authorizationRequest.uiLocales().toStringValues());
    }
    String params = httpQueryParams.params();
    return String.format("%s/%s?%s", normalizedBase, normalizedPath, params);
  }
}
