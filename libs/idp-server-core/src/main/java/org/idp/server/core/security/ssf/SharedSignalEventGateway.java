package org.idp.server.core.security.ssf;

public interface SharedSignalEventGateway {

  void send(SharedSignalEventRequest sharedSignalEventRequest);
}
