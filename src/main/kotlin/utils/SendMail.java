package utils;

import javax.activation.DataHandler;
import javax.activation.FileDataSource;
import javax.mail.*;
import javax.mail.internet.*;
import java.util.Properties;

class EmailAuthenticator extends javax.mail.Authenticator
{
    private String login   ;
    private String password;
    public EmailAuthenticator (String login, String password)
    {
        this.login    = login;
        this.password = password;
    }
    public PasswordAuthentication getPasswordAuthentication()
    {
        return new PasswordAuthentication(login, password);
    }
}

public class SendMail
{
    private Message message        = null;
    public String   SMTP_SERVER    = "smtp.gmail.com";
    public String   SMTP_Port      = "465";
    public String   SMTP_AUTH_USER = "";
    public String   SMTP_AUTH_PWD  = "";
    public String   EMAIL_FROM     = "chatter@it-port.ru";
    public String   FILE_PATH      = null;
    public String   REPLY_TO       = "chatter@it-port.ru";
    public String emailTo = "";
    public String thema = "";

    public SendMail() {
    }

    public void init(String emailTo, String thema) {
        this.emailTo = emailTo;
        this.thema = thema;
        Properties properties = new Properties();
        properties.put("mail.smtp.host"               , SMTP_SERVER);
        properties.put("mail.smtp.port"               , SMTP_Port  );
        properties.put("mail.smtp.auth"               , "true"     );
        properties.put("mail.smtp.ssl.enable"         , "false"     );
        properties.put("mail.smtp.socketFactory.class",
                "javax.net.ssl.SSLSocketFactory");
        try {
            Authenticator auth = new EmailAuthenticator(SMTP_AUTH_USER,
                    SMTP_AUTH_PWD);
            Session session = Session.getDefaultInstance(properties,auth);
            session.setDebug(false);

            InternetAddress email_from = new InternetAddress(EMAIL_FROM);
            InternetAddress email_to   = new InternetAddress(this.emailTo   );
            InternetAddress reply_to   = (REPLY_TO != null) ?
                    new InternetAddress(REPLY_TO) : null;
            message = new MimeMessage(session);
            message.setFrom(email_from);
            message.setRecipient(Message.RecipientType.TO, email_to);
            message.setSubject(this.thema);
            if (reply_to != null)
                message.setReplyTo (new Address[] {reply_to});
        } catch (AddressException e) {
            System.err.println(e.getMessage());
        } catch (MessagingException e) {
            System.err.println(e.getMessage());
        }
    }

    private MimeBodyPart createFileAttachment(String filepath)
            throws MessagingException
    {
        MimeBodyPart mbp = new MimeBodyPart();

        FileDataSource fds = new FileDataSource(filepath);
        mbp.setDataHandler(new DataHandler(fds));
        mbp.setFileName(fds.getName());
        return mbp;
    }

    public boolean sendMessage (final String text) throws Exception
    {
        boolean result = false;
        try {
            Multipart mmp = new MimeMultipart();
            MimeBodyPart bodyPart = new MimeBodyPart();
            bodyPart.setContent(text, "text/plain; charset=utf-8");
            mmp.addBodyPart(bodyPart);
            if (FILE_PATH != null) {
                MimeBodyPart mbr = createFileAttachment(FILE_PATH);
                mmp.addBodyPart(mbr);
            }
            message.setContent(mmp);
            Transport.send(message);
            result = true;
        } catch (Exception e){
            System.err.println(e.getMessage());
        }
        return result;
    }
}


