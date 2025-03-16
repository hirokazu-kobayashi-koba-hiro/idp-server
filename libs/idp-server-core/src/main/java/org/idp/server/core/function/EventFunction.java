package org.idp.server.core.function;

import org.idp.server.core.sharedsignal.Event;

public interface EventFunction {
  void handle(Event event);
}
