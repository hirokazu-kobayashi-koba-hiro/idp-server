package org.idp.server.core.authentication;

import org.idp.server.core.tenant.Tenant;

public interface AuthenticationTransactionQueryRepository {

    AuthenticationTransaction get(Tenant tenant, AuthenticationTransactionIdentifier identifier);
}
