package org.idp.server.oauth.sharedsignal;

public class Event {

    EventIdentifier identifier;
    EventType type;
    EventDescription description;
    IpAddress ipAddress;
    UserAgent userAgent;
    EventDetail detail;

    public Event() {}

    public Event(EventIdentifier identifier, EventType type, EventDescription description, IpAddress ipAddress, UserAgent userAgent, EventDetail detail) {
        this.identifier = identifier;
        this.type = type;
        this.description = description;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.detail = detail;
    }

    public EventIdentifier identifier() {
        return identifier;
    }

    public EventType type() {
        return type;
    }

    public EventDescription description() {
        return description;
    }

    public IpAddress ipAddress() {
        return ipAddress;
    }

    public UserAgent userAgent() {
        return userAgent;
    }

    public EventDetail detail() {
        return detail;
    }
}
