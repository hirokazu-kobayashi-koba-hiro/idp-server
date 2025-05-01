package org.idp.server.core.verifiablecredential;

import java.util.List;
import org.idp.server.core.oauth.vc.CredentialDefinition;
import org.idp.server.core.tenant.Tenant;
import org.idp.server.basic.type.oauth.Subject;

public interface VerifiableCredentialDelegate {

  // FIXME
  CredentialDelegateResponse getCredential(
      Tenant tenant, Subject subject, List<CredentialDefinition> credentialDefinitions);
}
