package org.idp.server.control_plane.base;

import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public record AdminAuthenticationContext(
    Tenant adminTenant, OAuthToken oAuthToken, User operator) {}
