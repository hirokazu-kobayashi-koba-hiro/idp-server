package org.idp.server.control_plane.management.identity.verification;

import org.idp.server.basic.type.security.RequestAttributes;
import org.idp.server.control_plane.management.identity.user.io.UserRegistrationRequest;
import org.idp.server.control_plane.management.identity.verification.io.IdentityVerificationConfigManagementResponse;
import org.idp.server.control_plane.management.identity.verification.io.IdentityVerificationConfigRegistrationRequest;
import org.idp.server.control_plane.management.identity.verification.io.IdentityVerificationConfigUpdateRequest;
import org.idp.server.core.identity.User;
import org.idp.server.core.identity.verification.configuration.IdentityVerificationConfigurationIdentifier;
import org.idp.server.core.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.core.token.OAuthToken;

public interface IdentityVerificationConfigManagementApi {

    IdentityVerificationConfigManagementResponse register(
            TenantIdentifier tenantIdentifier,
            User operator,
            OAuthToken oAuthToken,
            IdentityVerificationConfigRegistrationRequest request,
            RequestAttributes requestAttributes);

    IdentityVerificationConfigManagementResponse findList(
            TenantIdentifier tenantIdentifier,
            User operator,
            OAuthToken oAuthToken,
            int limit,
            int offset,
            RequestAttributes requestAttributes);

    IdentityVerificationConfigManagementResponse get(
            TenantIdentifier tenantIdentifier,
            User operator,
            OAuthToken oAuthToken,
            IdentityVerificationConfigurationIdentifier userIdentifier,
            RequestAttributes requestAttributes);

    IdentityVerificationConfigManagementResponse update(
            TenantIdentifier tenantIdentifier,
            User operator,
            OAuthToken oAuthToken,
            IdentityVerificationConfigurationIdentifier userIdentifier,
            IdentityVerificationConfigUpdateRequest request,
            RequestAttributes requestAttributes);

    IdentityVerificationConfigManagementResponse delete(
            TenantIdentifier tenantIdentifier,
            User operator,
            OAuthToken oAuthToken,
            IdentityVerificationConfigurationIdentifier userIdentifier,
            RequestAttributes requestAttributes);
}
