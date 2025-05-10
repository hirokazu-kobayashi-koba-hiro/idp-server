package org.idp.server.core.oidc.view;

import java.util.Objects;

import org.idp.server.basic.type.oauth.Error;
import org.idp.server.basic.type.oauth.ErrorDescription;
import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.oidc.OAuthRequestContext;


public class OAuthViewUrlResolver {

    public static String resolve(OAuthRequestContext context) {
        String base = context.tenant().domain().value();

        if (context.isPromptCreate()) {
            return buildUrl(base, "signup", context);
        }

        return buildUrl(base, "signin", context);
    }

    public static String resolveError(Tenant tenant, Error error, ErrorDescription errorDescription) {
        String base = tenant.domain().value();
        return String.format(
                "%s/error?error=%s&error_description=%s&tenant_id=%s",
                base,
                error.value(),
                errorDescription.value(),
                tenant.identifier().value());
    }

    private static String buildUrl(String base, String path, OAuthRequestContext context) {
        return String.format(
                "%s%s/?id=%s&tenant_id=%s",
                base,
                path,
                context.authorizationRequestIdentifier().value(),
                context.tenantIdentifier().value());
    }
}
