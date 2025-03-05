package org.idp.server.core.api;

import org.idp.server.core.sharedsignal.Event;

public interface EventApi {
  void register(Event event);
}
