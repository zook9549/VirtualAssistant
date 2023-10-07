# Virtual Assistant
This project was built to test out the different technologies emerging that allow deep-faked virtual assistants. It wasn't built for performance nor as an actual virtual assistant that could be used for production purposes.  The performance is too slow and it wasn't built with WebRTC or streaming in mind.  As new services emerge and those used here improve, that might become more realistic.  Feel free to submit pull requests for new providers.

Before creating a new persona, you should ensure you have consent from any real people being deep-faked.

## Setup
You'll need a few accounts setup to make this work and then access tokens added into the config.  Configuration should be added to a profile specific application.properties.  This is easiest done by creating application-default.properties and overriding the properties needed.
### AWS
Profile pictures, audio files, and persona videos are all stored in an S3 bucket.  You'll need an AWS account with a public S3 bucket configured.  Additionally, you'll need to setup a service account in AWS to have access to the bucket.
#### Configuration
1. aws.clientId=_client id for the service account created in AWS_
2. aws.authKey=_authorization key for the service account created in AWS_
3. aws.s3.bucket=_the name of the public bucket your created_

### Eleven Labs
Eleven Labs is used for creating the voice model.  It's easy to train, with 30-60 seconds usually creating a fairly high-quality representation of the personas voice.  Setup an account at https://elevenlabs.io/ and get your API keys.
#### Configuration
1. tts_key=_the API key obtained from Eleven Labs_

### ChatGPT
ChatGPT drives the conversation between the questions and the answers.  This is meant to be brief, but you can modify the prompts in the configuration to drive more thorough and robust answers.
#### Configuration
1. chatgpt.api.key=_api key for ChatGPT to the conversational chat API_

## Usage
### Setting Up Personas
Personas are setup using ./profiles.html.  Instructions are on the page to setup the voice model and avatar.  The description you choose for the person drive the prompt engineering behind the answers.

## Technical Details
The application is built upon Spring Boot, which gives an all-in-one platform running on Tomcat as a webserver.  This isn't intended to scale out, but this can easily be ported over to containers like K8/Docker.
