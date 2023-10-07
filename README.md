# Virtual Assistant
This project was built to test out the different technologies emerging that allow deep-faked virtual assistants. It wasn't built for performance nor as an actual virtual assistant that could be used for production purposes.  The performance is too slow, and it wasn't built with WebRTC or streaming in mind.  On average, it takes 3-4 seconds for a final generated video to display.  That's an eternity if you were to try to engage in a conversation.  As new services emerge and those used here improve, that might become more realistic.  Feel free to submit pull requests for new providers.

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

### D-ID
D-ID is what animates a profile picture with the audio to create animated avatar.  This is an area where I expect to see much better and cheaper alternatives to start emerging.  The requirements here are:
1. A decent API
2. Ability to use a human-based avatar based on a profile picture. 
3. Ability to provide audio directly for the voice OR ability to specify a trained voice model.

All other offerings I've found don't do a good job of supporting #2 and #3 together.

#### Configuration
1. did.key=_api key provided through D-ID_

## Usage
### Setting Up Personas
Personas are setup using ./profiles.html.  Instructions are on the page to setup the voice model and avatar.  The description you choose for the person drive the prompt engineering behind the answers.

## Technical Details
The application is built upon Spring Boot, which gives an all-in-one platform running on Tomcat as a webserver.  This isn't intended to scale out, but this can easily be ported over to containers like K8/Docker.
