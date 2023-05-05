package org.idp.server.handler.ciba.datasource.memory;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.idp.server.ciba.repository.BackchannelAuthenticationRequestRepository;
import org.idp.server.ciba.request.BackchannelAuthenticationRequest;
import org.idp.server.ciba.request.BackchannelAuthenticationRequestBuilder;
import org.idp.server.ciba.request.BackchannelAuthenticationRequestIdentifier;

public class BackchannelAuthenticationMemoryDataSource
    implements BackchannelAuthenticationRequestRepository {

  Map<BackchannelAuthenticationRequestIdentifier, BackchannelAuthenticationRequest> values =
      new HashMap<>();

  @Override
  public void register(BackchannelAuthenticationRequest request) {
    values.put(request.identifier(), request);
  }

  @Override
  public BackchannelAuthenticationRequest find(
      BackchannelAuthenticationRequestIdentifier identifier) {
    BackchannelAuthenticationRequest backchannelAuthenticationRequest = values.get(identifier);
    if (Objects.isNull(backchannelAuthenticationRequest)) {
      return new BackchannelAuthenticationRequestBuilder().build();
    }
    return backchannelAuthenticationRequest;
  }

  @Override
  public void delete(BackchannelAuthenticationRequestIdentifier identifier) {
    values.remove(identifier);
  }
}
