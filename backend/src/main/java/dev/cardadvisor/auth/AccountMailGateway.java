package dev.cardadvisor.auth;
/** Raw action tokens are delivered only to the owner mailbox, never returned by HTTP or logged. */
public interface AccountMailGateway {
 boolean ready();
 void send(String recipient,String purpose,String token);
}
