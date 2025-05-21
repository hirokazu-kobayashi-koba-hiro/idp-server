package org.idp.server.core.verifiable_credential;

import java.util.List;
import org.idp.server.basic.type.oauth.Subject;
import org.idp.server.core.oidc.vc.CredentialDefinition;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public interface VerifiableCredentialDelegate {

  // FIXME
  CredentialDelegateResponse getCredential(
      Tenant tenant, Subject subject, List<CredentialDefinition> credentialDefinitions);
}
