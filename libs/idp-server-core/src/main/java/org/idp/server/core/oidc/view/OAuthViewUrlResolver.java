/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.oidc.view;

import org.idp.server.basic.http.QueryParams;
import org.idp.server.basic.type.oauth.CustomParams;
import org.idp.server.basic.type.oauth.Error;
import org.idp.server.basic.type.oauth.ErrorDescription;
import org.idp.server.core.oidc.OAuthRequestContext;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class OAuthViewUrlResolver {

  public static String resolve(OAuthRequestContext context) {
    String base = context.tenant().domain().value();

    if (context.isPromptCreate()) {
      return buildUrl(base, "signup/index.html", context);
    }

    return buildUrl(base, "signin/index.html", context);
  }

  public static String resolveError(Tenant tenant, Error error, ErrorDescription errorDescription) {
    String base = tenant.domain().value();
    return String.format(
        "%s/error?error=%s&error_description=%s&tenant_id=%s",
        base, error.value(), errorDescription.value(), tenant.identifier().value());
  }

  private static String buildUrl(String base, String path, OAuthRequestContext context) {
    CustomParams customParams = context.authorizationRequest().customParams();
    QueryParams queryParams = new QueryParams(customParams.values());
    queryParams.add("id", context.authorizationRequestIdentifier().value());
    queryParams.add("tenant_id", context.tenantIdentifier().value());
    String params = queryParams.params();
    return String.format("%s/%s/?%s", base, path, params);
  }
}
