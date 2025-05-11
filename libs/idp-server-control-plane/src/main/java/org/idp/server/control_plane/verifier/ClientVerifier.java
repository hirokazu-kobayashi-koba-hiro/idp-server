package org.idp.server.control_plane.verifier;

import java.util.ArrayList;
import java.util.List;
import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.oidc.configuration.client.ClientConfiguration;
import org.idp.server.core.oidc.configuration.client.ClientConfigurationQueryRepository;

public class ClientVerifier {

  ClientConfigurationQueryRepository clientConfigurationQueryRepository;

  public ClientVerifier(ClientConfigurationQueryRepository clientConfigurationQueryRepository) {
    this.clientConfigurationQueryRepository = clientConfigurationQueryRepository;
  }

  public VerificationResult verify(Tenant tenant, ClientConfiguration clientConfiguration) {
    List<String> errors = new ArrayList<>();

    ClientConfiguration existing =
        clientConfigurationQueryRepository.find(tenant, clientConfiguration.clientIdentifier());
    if (existing.exists()) {
      errors.add("Client already exists");
    }

    if (!errors.isEmpty()) {
      return VerificationResult.failure(errors);
    }

    return VerificationResult.success();
  }
}
