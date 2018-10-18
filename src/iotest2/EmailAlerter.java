/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package iotest2;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import javax.mail.*;
import javax.mail.internet.*;
import javax.mail.Authenticator;
import javax.mail.PasswordAuthentication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
/**
 *
 * @author Jon
 */
public class EmailAlerter {
    
    Logger logger = LoggerFactory.getLogger(IoTest2.class);
    
    private String smtp_host_name;// = "smtp.powerxmail.com";
    private String smtp_auth_user;// = "jonghent1@nxlink.com";
    private String smtp_auth_pwd; // = "NxlinkMail!#523";
    private List<String> recipient_list = new ArrayList<String> (); 
    
    public EmailAlerter(String host,String user,String pwd){
        this.smtp_host_name = host;
        this.smtp_auth_user = user;
        this.smtp_auth_pwd = pwd;
    }
    
    public void addRecipient(String emailAddress){
        this.recipient_list.add(emailAddress);
    }
    
    public void raiseAlert(String alertSubject, String alertMessage){
        
        Properties props = new Properties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.host", this.smtp_host_name);
        props.put("mail.smtp.auth", "true");

        Authenticator auth = new SMTPAuthenticator();
        Session mailSession = Session.getDefaultInstance(props, auth);
        // uncomment for debugging infos to stdout
        mailSession.setDebug(true);
        
        try{
            Transport transport = mailSession.getTransport();
            MimeMessage message = new MimeMessage(mailSession);
            message.setContent(alertMessage, "text/plain");
            message.setSubject(alertSubject);
            message.setFrom(new InternetAddress("jonghent1@nxlink.com"));
            
            for(String to : this.recipient_list){
                //message.addRecipient(Message.RecipientType.TO, new InternetAddress("jfghent@gmail.com"));
                message.addRecipient(Message.RecipientType.TO, new InternetAddress(to));
            }
            
            transport.connect();
            transport.sendMessage(message,
                message.getRecipients(Message.RecipientType.TO));
            transport.close();
        } catch (MessagingException e){
            
        }
    }

    private class SMTPAuthenticator extends javax.mail.Authenticator {
        @Override
        public PasswordAuthentication getPasswordAuthentication() {
           String username = smtp_auth_user;
           String password = smtp_auth_pwd;
           return new PasswordAuthentication(username, password);
        }
    }
}
