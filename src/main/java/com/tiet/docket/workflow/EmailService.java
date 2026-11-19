package com.tiet.docket.workflow;

import com.tiet.docket.model.EmailLog;
import com.tiet.docket.repository.EmailLogRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Sends email through the SMTP server configured with the SPRING_MAIL_* settings.
 * Without one, the email is only saved to the outbox (Administrator > Email outbox),
 * so the project can be demonstrated without a real mail account.
 */
@Service
public class EmailService {

    private final ObjectProvider<JavaMailSender> mailSender;
    private final EmailLogRepository emailLogRepository;
    private final String from;

    public EmailService(ObjectProvider<JavaMailSender> mailSender, EmailLogRepository emailLogRepository,
                        @Value("${spring.mail.username:}") String from) {
        this.mailSender = mailSender;
        this.emailLogRepository = emailLogRepository;
        this.from = from.isBlank() ? "no-reply@docket.local" : from;
    }

    public boolean smtpConfigured() {
        return mailSender.getIfAvailable() != null;
    }

    @Transactional
    public EmailLog send(String to, String subject, String body) {
        EmailLog log = new EmailLog(to, subject, body);
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            log.setStatus(EmailLog.Status.NOT_SENT_NO_SMTP);
        } else {
            try {
                SimpleMailMessage message = new SimpleMailMessage();
                message.setFrom(from);
                message.setTo(to);
                message.setSubject(subject);
                message.setText(body);
                sender.send(message);
                log.setStatus(EmailLog.Status.SENT);
            } catch (MailException e) {
                // The workflow still goes on; the outbox shows the failure and the student can resend
                log.setStatus(EmailLog.Status.FAILED);
                log.setError(e.getMessage() == null ? "Mail server error" : e.getMessage().substring(0, Math.min(480, e.getMessage().length())));
            }
        }
        return emailLogRepository.save(log);
    }

    @Transactional(readOnly = true)
    public List<EmailLog> outbox() {
        return emailLogRepository.findAllByOrderByCreatedAtDesc();
    }
}
