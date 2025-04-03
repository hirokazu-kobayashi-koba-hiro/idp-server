package org.idp.server.adapters.springboot.event;

import java.util.function.Consumer;
import org.idp.server.core.sharedsignal.Event;

public class EventRunnable implements Runnable {

  Event event;
  Consumer<Event> handler;

  public EventRunnable(Event event, Consumer<Event> handler) {
    this.event = event;
    this.handler = handler;
  }

  public Event getEvent() {
    return event;
  }

  @Override
  public void run() {
    handler.accept(event);
  }
}
