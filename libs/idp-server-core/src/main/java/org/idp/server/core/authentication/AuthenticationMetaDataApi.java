package org.idp.server.core.authentication;

import org.idp.server.core.authentication.fidouaf.FidoUafExecutionResult;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;

public interface AuthenticationMetaDataApi {

  FidoUafExecutionResult getFidoUafFacets(TenantIdentifier tenantIdentifier);
}
