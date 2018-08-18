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
Logs are written to stdout and to logs/${APP}[-n].log (see run) in JSON bunyan
format. n indicates a log rotation between 1-9 in addition to the 'current' log 
file logs/${APP}.log. To view a log file simply run 'bunyan logs/${APP}[-n].log'.
To view stdout while developing use 'run dev | bunyan'.

### Installation
To install the bunyan CLI first install nodejs with '[sudo] yum install
nodejs'. Then install bunyan using '[sudo] npm i -g bunyan'.

## Updating dependencies
ONCE only: 'cp src/dev/settings.xml ~/.m2' to add Clojars and Artifactory
repo references. If you already have a ~/.m2/settings file, simply merge the
relevant 'server' and 'repository' sections from src/dev/settings.xml into 
your existing ~/.m2/settings.xml file.

run 'bin/dcheck' to generate a pom.xml file and/or to list 'out of date' dependencies.

Use your favorite editor to update the out of date dependencies in either ./deps.edn
or ${HOME}/.clojure/deps.edn. Be sure to sync the latter back to src/dev/deps.edn when 
committing in git. The latter contains dependendencies used during development
only such as nRepl, static code/idiom analysis, testing, debugging, profiling, etc.. 
The former contains dependencies required solely by the uS you're building.

### Install Dependency Management Tools
To 'run bin/dcheck' or 'run bin/dcheck-mvn' gradle or maven needs to be
installed respectively. Use your package manager to install either. To install 
gradle manually, visit https://services.gradle.org/distributions and dowload the
latest version [-bin|-all] and unzip. Ensure gradle's bin directory is in your $PATH.
To install maven manually, visit https://maven.apache.org/download.cgi and
download the latest version and un[zip|tar]. Ensure apache-maven's bin directory is
in your $PATH.

### Consuming jars from Artifactory
To consume jars from Artifactory (e.g. deps.edn) you need to complete the following steps:
   - Install the artifactory certificate in the cacerts file of your JDK.
      - See comments in script bin/add-cert-java for details.
   - Embed your artifactory credentials in ~/.m2/settings.xml
      - See the artifactory server section in src/dev/settings.xml for details.
   - Augment ~/.m2/settings.xml with artifactory repo details
      - See the artifactory repository section in src/dev/settings.xml for details.

If you have no ~/.m2/settings.xml file, simply copy src/dev/settings.xml to
~/.m2 and update the artifactory server section with your actual artifactory 
credentials. Else simply merge the artifactory server and repository sections 
into your existing ~/.m2/settings file. In either case, be sure to read and 
follow the instructions described in the password encryption URL referenced in 
the comments right above the server section. Also, chmod go-rwx ~/.m2/settings.xml.

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
with a git revision resulting from 'git describe --tag --dirty'. Hence, your 
repo requires at least one tag. If your cloned repo does not contain a tag, 
create a tag at the first commit using 'git tag v0.0 sha-first-commit'. Be sure 
to add tags to your repo as you release updates to production. To push tags 
back to the original repo use 'git push --tags orgin'. If you work within a
git repo/workspace and don't have a tag, a 'v0.0' tag will be generated for 
you on the first 'run' and will be associated with your first commit. If you 
are not working in a git repo/workspace, a 'No-Git' pseudo tag will be 
generated for you on the first 'run'.

### Install Packaging Tools
To create jars and/or docker containers either gradle or maven needs to be intalled.
Use your package manager to install either. To install gradle manually, 
visit https://services.gradle.org/distributions and dowload the
latest version [-bin|-all] and unzip. Ensure gradle's bin directory is in your $PATH.
To install maven manually, visit https://maven.apache.org/download.cgi and
download the latest version and un[zip|tar]. Ensure apache-maven's bin directory is
in your $PATH. To use/install Docker on Windows or Mac see
https://www.docker.com/products/docker-desktop. On linux, consult your
distro's Docker documentation.

### Publish to Artifactory
If you need to publish the final library jar and/or executable uberjar to artifactory,
simply run 'bin/publish-art' after you've built the production jar using 'run
bundle' and have tested the jar using 'run dev jar | bunyan'. Before publishing 
to Artifactory you need to have setup your artifactory credentials in 
~/.artifactory-creds. Be sure to 'chmod go-rwx' this file. See 'bin/publish-art' 
for details.

## Invocation
Select one of the methods below to start the micro service
   - run 'run dev [jar]|prod'
   - See Dockerfile on how to run the docker container

