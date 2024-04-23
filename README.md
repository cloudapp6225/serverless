# Google Cloud Function for Pub/Sub

This project contains a Google Cloud Function written in Java that handles Pub/Sub messages. The function is responsible for processing incoming messages from Pub/Sub, extracting relevant data, sending emails using MailGun SMTP, and updating a PostgreSQL database using [serverless VPC connector](https://github.com/cloudapp6225/tf-gcp-infra)
.

## Setup

1. **Prerequisites:**
   - Google Cloud Platform account
   - Java Development Kit (JDK) installed
   - Maven installed

2. **Clone the repository:**
   ```bash
   git clone https://github.com/your-username/gcfv2pubsub.git
   cd gcfv2pubsub
3. **Environment Variables:**

Make sure to set the following environment variables:
- `mailgun_email`: Email address used for sending emails via MailGun.
- `api_key`: API key for MailGun authentication.
- `db_ip`: private IP address of the PostgreSQL database server.
- `password`: Password for the PostgreSQL database.

# Deploy to Google Cloud Platform

```bash
gcloud functions deploy pubSubFunction \
    --entry-point=gcfv2pubsub.PubSubFunction \
    --runtime=java17 \
    --trigger-topic=your-topic-name \
    --memory=512MB \
    --region=your-region
