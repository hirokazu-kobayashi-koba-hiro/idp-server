package org.idp.server.core;

import org.idp.server.core.sharedsignal.Event;

public interface EventApi {
  void handle(Event event);
}
