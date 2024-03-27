package gcfv2pubsub;

import com.google.cloud.functions.CloudEventsFunction;
import com.google.events.cloud.pubsub.v1.Message;
import com.google.events.cloud.pubsub.v1.MessagePublishedData;
import com.google.gson.Gson;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.cloudevents.CloudEvent;

import javax.mail.Authenticator;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.sql.DataSource;
import java.sql.*;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Logger;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class PubSubFunction implements CloudEventsFunction {
  private static final Logger logger = Logger.getLogger(PubSubFunction.class.getName());

  @Override
  public void accept(CloudEvent event) {
    // Get cloud event data as JSON string
    String cloudEventData = new String(event.getData().toBytes());
    // Decode JSON event data to the Pub/Sub MessagePublishedData type
    Gson gson = new Gson();
    MessagePublishedData data = gson.fromJson(cloudEventData, MessagePublishedData.class);
    // Get the message from the data
    Message message = data.getMessage();
    // Get the base64-encoded data from the message & decode it
    String encodedData = message.getData();
    String decodedData = new String(Base64.getDecoder().decode(encodedData));
    //Map<String, String> msgAttributes = message.getAttributes();
    Map<String, String> jsonMap = gson.fromJson(decodedData, Map.class);
    logger.info(jsonMap.get("uuid"));
    mailGunSMTP(jsonMap.get("uuid"), jsonMap.get("username"));
    String uuid = jsonMap.get("uuid");
    DataSource ds = createConnectionPool();
    Connection con = null;
    try {
      con = ds.getConnection();
      Statement statement = con.createStatement();
      ResultSet resultSet = statement.executeQuery("SELECT * FROM email_verification");
      while(resultSet.next()) {
        String uname = resultSet.getString("email");
        logger.info(uname + "from database");
      }
      LocalDateTime currentTimestamp = LocalDateTime.now();
      String formattedTimestamp = currentTimestamp.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS"));
      String link = "http://skynetx.me:8080/v1/verify/" + uuid;
      String sql = "UPDATE email_verification SET status = ?, link = ?, sent_timestamp = ? WHERE user_id = ?";
      PreparedStatement updateStmt = con.prepareStatement(sql);
      updateStmt.setString(1, "sent");
      updateStmt.setString(2, link);
      updateStmt.setString(3, formattedTimestamp);
      updateStmt.setString(4, uuid);
      int rowsAffected = updateStmt.executeUpdate();
      System.out.println("Rows affected: " + rowsAffected);
      logger.info(String.valueOf(rowsAffected));
    } catch (SQLException e) {
      logger.info(e.getMessage());
      throw new RuntimeException(e);
    }
    finally {
      if(con != null) {
        try {
          con.close();
        } catch (SQLException e) {
          logger.warning("Error closing connection to database");
          throw new RuntimeException(e);
        }
      }
    }
    // Log the message
    logger.info("Pub/Sub message: " + decodedData);
  }

  public static void sendEmail(Session session, String toEmail, String subject, String body){
    try
    {
      MimeMessage msg = new MimeMessage(session);
      msg.addHeader("Content-type", "text/HTML; charset=UTF-8");
      msg.addHeader("format", "flowed");
      msg.addHeader("Content-Transfer-Encoding", "8bit");
      msg.setFrom(new InternetAddress("no_reply@skynetx.me", "NoReply-JD"));
      msg.setReplyTo(InternetAddress.parse("no_reply@skynetx.me", false));
      msg.setSubject(subject, "UTF-8");
      msg.setText(body, "UTF-8");
      msg.setSentDate(new Date());
      msg.setRecipients(javax.mail.Message.RecipientType.TO, InternetAddress.parse(toEmail, false));
      System.out.println("Message is ready");
      logger.info("Message is ready");
      Transport.send(msg);
      System.out.println("Email Sent Successfully!!");
      logger.info("Email Sent Successfully!!");
    }
    catch (Exception e) {
      logger.info(e.getMessage());
      e.printStackTrace();
    }
  }

  public void mailGunSMTP(String uuid, String toEmail) {
    final String fromEmail = System.getenv("mailgun_email");
    final String password = System.getenv("api_key");
    //final String toEmail = "vinay21031998@gmail.com";

    System.out.println("SSLEmail Start");
    Properties props = new Properties();
    props.put("mail.smtp.host", "smtp.mailgun.org");
    props.put("mail.smtp.port", "587");
    props.put("mail.smtp.auth", "true");
    props.put("mail.smtp.starttls.enable", "true");

    Authenticator auth = new Authenticator() {
      protected PasswordAuthentication getPasswordAuthentication() {
        return new PasswordAuthentication(fromEmail, password);
      }
    };

    Session session = Session.getInstance(props, auth);
    System.out.println("Session created");
    logger.info("Session created");
    String body = "http://skynetx.me:8080/v1/verify/" + uuid;
    sendEmail(session, toEmail,"mail from vk Testing Subject", body);
  }
  public static DataSource createConnectionPool() {
    HikariConfig hikariConfig = new HikariConfig();
    String db_ip = System.getenv("db_ip");
    String password = System.getenv("password");
    hikariConfig.setJdbcUrl(String.format("jdbc:postgresql://%s:%s/%s", db_ip, "5432", "webapp"));
    hikariConfig.setUsername("webapp");
    hikariConfig.setPassword(password);
    logger.info("DB Connected");
    return new HikariDataSource(hikariConfig);
  }
}