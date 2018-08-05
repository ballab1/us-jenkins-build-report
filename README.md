# Kafka uS-lib events consumer
uS-lib consumer microservice consuming JSON messages from Kafka inserting them into a sink database.

## Clojure
### Installation
Follow instructions on how to install Clojure for your platform here (trivial):
   https://clojure.org/guides/getting_started

To enable interactive development:
   - run 'cp src/dev/deps.edn to ${HOME}/.clojure/deps.edn'

### Development
To modify the micro service's (uS) behavior edit src/main/clj/msa/core.clj.
All other code is skeletal/supporting (Kafka cons/prod, msg validation, dead letter queue (dlq), database, heartbeat, etc.).

To enable interactive development:
   - run 'cp src/dev/deps.edn to ${HOME}/.clojure/deps.edn'

To develop interactively from your editor:
   - Setup your editor and understand the nRepl starting here: https://github.com/clojure/tools.nrepl#connecting-to-an-nrepl-server 
   - Then run 'run dev' to start the nRepl for interactive development
   - Start your editor
   - Optionally setup 'screen' or 'tmux' to view and interact with your editor and the nRepl side by side

## Logging
Logs are written to stdout and to ${APP}.log (see run) in JSON bunyan
format. To view the log file simply run 'bunyan ${APP}.log'. To 
view stdout while developing use 'run dev | bunyan'.

### Installation
To install the bunyan CLI first install nodejs with '[sudo] yum install
nodejs'. Then install bunyan using '[sudo] npm i -g bunyan'.

## Updating dependencies
ONCE only: 'cp src/dev/settings.xml ~/.m2' to add Clojars.

run 'bin/dcheck' to generate a pom.xml file and/or to list 'out of date' dependencies

Use your favorite editor to update the out of date dependencies in either ./deps.edn
or ${HOME}/.clojure/deps.edn. Be sure to sync the latter back to src/dev/deps.edn when 
committing in git.

### Installation
To 'run bin/dcheck' or 'run bin/dcheck-mvn' gradle or maven needs to be
installed respectively. Use your package manager to install either. To install 
gradle manually, visit https://services.gradle.org/distributions and dowload the
latest version [-bin|-all] and unzip. Ensure gradle's bin directory is in your $PATH.
To install maven manually, visit https://maven.apache.org/download.cgi and
download the latest version and un[zip|tar]. Ensure apache-maven's bin directory is
in your $PATH.

## Packaging for production
Packaging will require a pom.xml file. Generate one manually by running 
'bin/dcheck' if desired or let the 'run bundle' commands below generate one 
automatically. See 'Updating dependencies'. Choose one of the following 
methods to package for production:

- run 'run bundle' to generate a jar in the deploy directory using gradle
- run 'run bundle mvn' to generate a jar in the deploy directory using maven
- See Dockerfile on how to build a production container

### Versioning of jars|containers and repo tags
Jars and docker containers are automatically versioned based on a tag combined 
with a git revision based on 'git describe --tag --dirty'. Hence, your repo 
requires at least one tag. If your cloned repo does not contain a base tag, 
create a tag at the first commit using 'git tag v0.0 sha-first-commit'. Be sure to 
add tags to your repo as you release updates to production. To push tags 
back to the original repo use 'git push --tags orgin'.

### Installation
To create jars and/or docker containers either gradle or maven needs to be intalled.
Use your package manager to install either. To install gradle manually, 
visit https://services.gradle.org/distributions and dowload the
latest version [-bin|-all] and unzip. Ensure gradle's bin directory is in your $PATH.
To install maven manually, visit https://maven.apache.org/download.cgi and
download the latest version and un[zip|tar]. Ensure apache-maven's bin directory is
in your $PATH.

## Invocation
Select one of the methods below to start the micro service
   - run 'run dev [jar]|prod'
   - See Dockerfile on how to run the docker container

