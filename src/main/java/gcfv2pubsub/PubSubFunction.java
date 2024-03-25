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
    Map<String, String> msgAttributes = message.getAttributes();
    logger.info(msgAttributes.get("username"));
    mailGunSMTP(msgAttributes.get("userId"), msgAttributes.get("username"));
    String userId = msgAttributes.get("userId");
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
      String sql = "UPDATE email_verification SET status = 'sent' WHERE user_id = ?";
      PreparedStatement updateStmt = con.prepareStatement(sql);
        updateStmt.setString(1, userId);
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
    String decodedData = new String(Base64.getDecoder().decode(encodedData));
    // Log the message
    logger.info("Pub/Sub message: " + decodedData);
  }
}
