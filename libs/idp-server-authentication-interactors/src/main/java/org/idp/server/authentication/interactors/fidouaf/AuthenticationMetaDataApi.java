package org.idp.server.authentication.interactors.fidouaf;

import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;

public interface AuthenticationMetaDataApi {

  FidoUafExecutionResult getFidoUafFacets(TenantIdentifier tenantIdentifier);
}
