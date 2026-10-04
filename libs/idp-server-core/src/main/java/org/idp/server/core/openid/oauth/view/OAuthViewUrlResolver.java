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

import java.util.Map;
import org.idp.server.core.openid.oauth.OAuthRequestContext;
import org.idp.server.core.openid.oauth.request.AuthorizationRequest;
import org.idp.server.core.openid.oauth.type.oauth.CustomParamSource;
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

    return resolvePageUrl(variant, uiConfiguration, tenant, context);
  }

  /**
   * The origin and the path, always taken from the same side.
   *
   * <p>A path only means something on the deployment that serves it, so the two cannot be inherited
   * independently: a variant that moves the origin and renames one page would otherwise send the
   * pages it did not name to a path that exists only on the default deployment.
   *
   * <p>Three cases, in order. The variant names this page, so it is served from the variant's
   * origin. The variant names no page at all, so it only moves the origin and the default paths
   * still apply there. The variant declares its own scheme but not this page, so the request falls
   * back to the default deployment whole rather than mixing the two.
   */
  private static String resolvePageUrl(
      UIViewVariant variant,
      UIConfiguration uiConfiguration,
      Tenant tenant,
      OAuthRequestContext context) {

    boolean promptCreate = context.isPromptCreate();
    String variantPage = promptCreate ? variant.signupPage() : variant.signinPage();
    String defaultPage = promptCreate ? uiConfiguration.signupPage() : uiConfiguration.signinPage();
    String variantBase = variant.hasBaseUrl() ? variant.baseUrl() : tenant.baseUrl();

    if (variantPage != null && !variantPage.isEmpty()) {
      return buildUrl(variantBase, variantPage, context);
    }
    if (!variant.hasPageOverrides()) {
      return buildUrl(variantBase, defaultPage, context);
    }
    return buildUrl(tenant.baseUrl(), defaultPage, context);
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
   * <p>The name stays in the custom parameters, so it reaches the page in view-data and, unless it
   * came in a request object, on the URL, and it is stored with the authorization request.
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
    HttpQueryParams httpQueryParams = new HttpQueryParams(customParamsOnUrl(authorizationRequest));
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

  /**
   * The custom parameters carried on the URL to the page. All of them are in view-data.
   *
   * <p>A value the client put in a request object is left off (Issue #1907). The client chose to
   * send it signed, possibly encrypted, and the URL would put it in the browser history, in Referer
   * headers and in the page server's access log. A pushed authorization request is read back from
   * storage at the authorization endpoint, where the source of each value is no longer known; when
   * it held a request object, no custom parameter is carried, since any of them may have come from
   * it.
   */
  private static Map<String, String> customParamsOnUrl(AuthorizationRequest authorizationRequest) {
    CustomParams customParams = authorizationRequest.customParams();
    if (authorizationRequest.hasRequest() && !customParams.sourcesKnown()) {
      return Map.of();
    }
    return customParams.valuesNotFrom(CustomParamSource.REQUEST_OBJECT);
  }
}
