package org.idp.server.core.sharedsignal;

public interface SharedSignalEventGateway {

  void send(SharedSignalEventRequest sharedSignalEventRequest);
}
