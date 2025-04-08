package org.idp.server.core.security.hook.ssf;

public interface SharedSignalEventGateway {

  void send(SharedSignalEventRequest sharedSignalEventRequest);
}
