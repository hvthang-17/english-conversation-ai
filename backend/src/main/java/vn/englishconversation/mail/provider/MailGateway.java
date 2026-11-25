package vn.englishconversation.mail.provider;

public interface MailGateway {
  boolean available();

  void send(String recipient, String subject, String text);
}
