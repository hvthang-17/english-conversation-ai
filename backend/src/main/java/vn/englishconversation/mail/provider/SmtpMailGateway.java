package vn.englishconversation.mail.provider;

import org.springframework.beans.factory.annotation.Value;
import vn.englishconversation.config.features.FeatureEnabled;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;

@Component
@FeatureEnabled(FeatureEnabled.Feature.CORE)
public class SmtpMailGateway implements MailGateway {
  private final JavaMailSenderImpl sender = new JavaMailSenderImpl();
  private final boolean enabled;
  private final String from;

  public SmtpMailGateway(
      @Value("${app.mail.enabled:false}") boolean enabled,
      @Value("${app.mail.host:}") String host,
      @Value("${app.mail.port:587}") int port,
      @Value("${app.mail.username:}") String username,
      @Value("${app.mail.password:}") String password,
      @Value("${app.mail.from:}") String from,
      @Value("${app.mail.starttls:true}") boolean tls) {
    this.enabled = enabled;
    this.from = from;
    if (enabled && (host.isBlank() || from.isBlank()))
      throw new IllegalArgumentException("SMTP host/from required");
    sender.setHost(host);
    sender.setPort(port);
    sender.setUsername(username);
    sender.setPassword(password);
    sender.setDefaultEncoding("UTF-8");
    var props = sender.getJavaMailProperties();
    props.setProperty("mail.smtp.auth", Boolean.toString(!username.isBlank()));
    props.setProperty("mail.smtp.starttls.enable", Boolean.toString(tls));
    props.setProperty("mail.smtp.starttls.required", Boolean.toString(tls));
    props.setProperty("mail.smtp.connectiontimeout", "5000");
    props.setProperty("mail.smtp.timeout", "5000");
    props.setProperty("mail.smtp.writetimeout", "5000");
  }

  public boolean available() {
    return enabled;
  }

  public void send(String recipient, String subject, String text) {
    if (!enabled) throw new IllegalStateException("SMTP disabled");
    var message = new SimpleMailMessage();
    message.setFrom(from);
    message.setTo(recipient);
    message.setSubject(subject);
    message.setText(text);
    sender.send(message);
  }
}
