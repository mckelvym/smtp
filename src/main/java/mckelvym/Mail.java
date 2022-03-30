package mckelvym;

import static java.util.Objects.requireNonNull;

import com.google.common.base.Strings;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Mail
{
    public static void main(final String[] p_Args) throws Exception
    {
	new Mail().send();
    }

    private static final Logger log = LoggerFactory.getLogger(Mail.class);

    private Multipart newMultiPartChild(final Multipart p_ParentMultipart,
                                          final String p_MultipartType) throws MessagingException
    {
        final Multipart multiPart = new MimeMultipart(p_MultipartType);
        final MimeBodyPart mbp = new MimeBodyPart();
        p_ParentMultipart.addBodyPart(mbp);
        mbp.setContent(multiPart);
        return multiPart;
    }

    public boolean send() throws Exception
    {
	Properties prop = new Properties();
        try (InputStream input = new FileInputStream("application.properties")) {
	    prop.load(input);
	}

        final String server = requireNonNull(prop.getProperty("mail.host"));
	final String port = requireNonNull(prop.getProperty("mail.port"));
	final String starttls = requireNonNull(prop.getProperty("mail.starttls"));
	final String username = requireNonNull(prop.getProperty("mail.username"));
	final String password = requireNonNull(prop.getProperty("mail.password"));
        final String from = requireNonNull(prop.getProperty("mail.from"));
        final String to = requireNonNull(prop.getProperty("mail.to"));
        final String subject = requireNonNull(prop.getProperty("mail.subject"));
        final String body = requireNonNull(prop.getProperty("mail.body"));
        final long timeout = 5000;
        final long retryMax = 2;
        final long retrySleep = 1000;

        final Properties props = new Properties();
        props.put("mail.transport.protocol", "SMTP");
        props.put("mail.smtp.host", server);
        props.put("mail.smtp.connectiontimeout", String.valueOf(timeout));
        props.put("mail.smtp.timeout", String.valueOf(timeout));
	props.put("mail.smtp.port", port);

        Session session = null;

        if (!Strings.isNullOrEmpty(username)
            && !Strings.isNullOrEmpty(password))
            {
                props.put("mail.smtp.auth", "true");
                props.put("mail.smtp.starttls.enable", starttls);

                session = Session.getInstance(props, new javax.mail.Authenticator()
                    {
                        @Override
                        protected PasswordAuthentication getPasswordAuthentication()
                        {
                            return new PasswordAuthentication(username, password);
                        }
                    });
            }
        else
            {
                props.put("mail.smtp.auth", "false");
                session = Session.getInstance(props);
            }

        final MimeMessage mimeMessage = new MimeMessage(session);
        final Multipart parentMultipart = new MimeMultipart("mixed");
        final Multipart multiPartMixedAlternative = newMultiPartChild(
                                                                      parentMultipart, "alternative");

        try
            {
                mimeMessage.setSubject(subject);
            }
        catch (final MessagingException e)
            {
                final String message = String
                    .format("Unable to set email subject: %s", subject);
                throw new MessagingException(message, e);
            }

        mimeMessage.setFrom(new InternetAddress(from));
        mimeMessage.setRecipients(Message.RecipientType.TO,
                                  InternetAddress.parse(to));

        /**
         * Text body
         */
        MimeBodyPart bodyPart = new MimeBodyPart();
        final StringBuilder combinedBody = new StringBuilder();
        combinedBody.append(String.format("%s%n", String.valueOf(body)));

        bodyPart.setContent(combinedBody.toString(), "text/plain");
        multiPartMixedAlternative.addBodyPart(bodyPart);
        /**
         * Set message content
         */
        mimeMessage.setContent(parentMultipart);

        for (int i = 0; i < retryMax; i++)
            {
                try
                    {
			log.info("Sending...");
                        Transport.send(mimeMessage);
			log.info("Sent.");
                        return true;
                    }
                catch (final com.sun.mail.util.MailConnectException e)
                    {
                        log.warn("Sleeping before retry #" + (i + 1), e);
                        try
                            {
                                Thread.sleep(retrySleep);
                            }
                        catch (final InterruptedException ie)
                            {
                                log.warn("Interrupted.", ie);
                                /**
                                 * Ignore
                                 */
                            }
			log.error("Failed.", e);
			return false;
                    }
            }
	log.error("Failed.");
	return false;
    }
}
