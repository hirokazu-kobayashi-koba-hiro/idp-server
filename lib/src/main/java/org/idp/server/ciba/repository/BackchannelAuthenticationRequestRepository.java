package org.idp.server.ciba.repository;

import org.idp.server.ciba.request.BackchannelAuthenticationRequest;
import org.idp.server.ciba.request.BackchannelAuthenticationRequestIdentifier;

public interface BackchannelAuthenticationRequestRepository {

  void register(BackchannelAuthenticationRequest request);

  BackchannelAuthenticationRequest find(BackchannelAuthenticationRequestIdentifier identifier);

  void delete(BackchannelAuthenticationRequestIdentifier identifier);
}
