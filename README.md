# Virtual Assistant Project

This project explores the capabilities of emerging deep-fake virtual assistant technologies. Although it serves as a testing ground for these technologies, it is not optimized for high performance nor intended for production use.

- Performance Insight: Current performance metrics show a 3-4 second delay for the final video display, which is suboptimal for real-time conversations. However, improvements are anticipated as the incorporated technologies evolve.

- WebRTC & Streaming: The current build does not integrate WebRTC or streaming.

- Contribution: Community contributions are welcome! Please feel free to submit pull requests, especially for new providers.

> Important: Before creating a new persona, always obtain consent if real individuals are being represented.

---

## Setup & Configuration

To initiate the virtual assistant, configure certain accounts and ensure the correct tokens are in place. Ideally, configurations should be added to a profile-specific `application.properties`, which can be easily managed through `application-default.properties`.

### 1. AWS (Amazon Web Services)

- Purpose: AWS's S3 bucket houses profile images, audio recordings, and persona videos.

- Requirements:

An AWS account with a configured public S3 bucket.
A service account in AWS with bucket access.
Configuration Keys:
```markdown
aws.clientId=YOUR_AWS_CLIENT_ID
aws.authKey=YOUR_AWS_AUTH_KEY
aws.s3.bucket=YOUR_S3_BUCKET_NAME
```

### 2. Eleven Labs

- Purpose: For voice model generation, with a short training time of 30-60 seconds.

- SignUp: Register at Eleven Labs to obtain API keys.

Configuration Key:
```markdown
tts_key=YOUR_ELEVEN_LABS_API_KEY
```

### 3. ChatGPT

- Purpose: Drives the dialogue dynamics between user queries and responses.

Configuration Key:
```markdown
chatgpt.api.key=YOUR_CHATGPT_API_KEY
```

### 4. D-ID

- Purpose: Animates avatars synchronized with audio.

- Requirements:

Reliable API.
Support for human-like avatars from images.
Direct audio provision or the ability to use trained voice models.
> Note: Many available solutions lack robust support for requirements 2 and 3.

Configuration Key:
```markdown
did.key=YOUR_DID_API_KEY
```

---

## Usage Guide

### Persona Creation

Use the `./profiles.html` for persona setup. The provided on-page instructions will guide you through voice model and avatar configurations. The chosen persona description influences the response dynamics of the assistant.

---

## Technical Aspects

Built on Spring Boot, this application offers a comprehensive platform that runs on Tomcat. While it's not designed for extensive scalability, it's compatible with container solutions like Kubernetes (K8) or Docker.