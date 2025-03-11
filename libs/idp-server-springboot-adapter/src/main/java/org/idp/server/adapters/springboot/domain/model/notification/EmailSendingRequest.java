package org.idp.server.adapters.springboot.domain.model.notification;

public class EmailSendingRequest {
  String from;
  String to;
  String subject;
  String body;

  public EmailSendingRequest(String from, String to, String subject, String body) {
    this.from = from;
    this.to = to;
    this.subject = subject;
    this.body = body;
  }

  public String from() {
    return from;
  }

  public String to() {
    return to;
  }

  public String subject() {
    return subject;
  }

  public String body() {
    return body;
  }
}
