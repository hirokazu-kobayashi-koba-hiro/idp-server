package org.idp.server.core.ciba.repository;

import org.idp.server.core.ciba.request.BackchannelAuthenticationRequest;
import org.idp.server.core.ciba.request.BackchannelAuthenticationRequestIdentifier;

public interface BackchannelAuthenticationRequestRepository {

  void register(BackchannelAuthenticationRequest request);

  BackchannelAuthenticationRequest find(BackchannelAuthenticationRequestIdentifier identifier);

  void delete(BackchannelAuthenticationRequestIdentifier identifier);
}
