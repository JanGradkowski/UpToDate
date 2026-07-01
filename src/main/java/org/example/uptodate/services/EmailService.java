package org.example.uptodate.services;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
@Service
public class EmailService {
    private final JavaMailSender mailSender;
    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }
    @Async
    public void sendEmail(String toEmail, String username) {
        SimpleMailMessage mailMessage = new SimpleMailMessage();
        mailMessage.setFrom("your_email@gmail.com");
        mailMessage.setTo(toEmail);
        mailMessage.setSubject("UpToDate welcome message");
        String body = "Hi " + username + ",\n\n" +
                "Thank you for registering for UpToDate! We are thrilled to have you join our community.\n\n" +
                "Capture the moment. Share the world.\n\n" +
                "Best,\n" +
                "The UpToDate Team";

        mailMessage.setText(body);
        mailSender.send(mailMessage);
    }
    @Async
    public void sendPasswordChangeCode(String toEmail, String code){
        SimpleMailMessage mailMessage = new SimpleMailMessage();
        mailMessage.setFrom("your_email@gmail.com");
        mailMessage.setTo(toEmail);
        mailMessage.setSubject("Password Change Code");
        String body = "You recently requested to change your password.\n\n" +
                "Your 6-digit verification code is: " + code + "\n\n" +
                "If you did not request this change, please secure your account immediately.\n\n" +
                "The UpToDate Security Team";
        mailMessage.setText(body);
        mailSender.send(mailMessage);
    }

}
