package org.idp.server.core;

import org.idp.server.core.api.EventApi;
import org.idp.server.core.basic.sql.Transactional;
import org.idp.server.core.sharedsignal.Event;
import org.idp.server.core.sharedsignal.EventRepository;

@Transactional
public class EventApiImpl implements EventApi {

  EventRepository eventRepository;

  public EventApiImpl(EventRepository eventRepository) {
    this.eventRepository = eventRepository;
  }

  @Override
  public void register(Event event) {
    eventRepository.register(event);
  }
}
