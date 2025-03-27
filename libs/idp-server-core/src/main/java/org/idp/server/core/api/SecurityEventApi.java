package org.idp.server.core.api;

import org.idp.server.core.sharedsignal.Event;

public interface SecurityEventApi {
  void handle(Event event);
}
