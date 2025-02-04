# Kafka uS-lib events consumer
uS-lib consumer/producer library jar used by microservices consuming JSON messages from Kafka inserting them into a sink database.

## Clojure
### Installation
Follow instructions on how to install Clojure for your platform here (trivial):
   https://clojure.org/guides/getting_started

Or simply run './bin/install-clojure' to install clojure on either OSX or Linux.

To enable interactive development:
   - run 'cp src/dev/deps.edn to ${HOME}/.clojure/deps.edn'

### Development
To modify the micro service's (uS) behavior edit src/main/clj/msa/core.clj.
All other code is skeletal/supporting (Kafka cons/prod, msg validation, dead letter queue (dlq), database, heartbeat, etc.).

To enable interactive development:
   - run 'cp src/dev/deps.edn to ${HOME}/.clojure/deps.edn'

To develop interactively from your editor:
   - Setup your editor and understand the nRepl starting here: https://github.com/clojure/tools.nrepl#connecting-to-an-nrepl-server
   - Then run 'make repl' to start an nRepl for interactive development
   - Start your editor
   - Optionally setup 'screen' or 'tmux' to view and interact with your editor and the nRepl side by side

## Logging
Logs are written to stdout and to logs/${APP}[-n].log (see run) in JSON bunyan
format. n indicates a log rotation between 1-9 in addition to the 'current' log
file logs/${APP}.log. To view a log file simply run 'bunyan logs/${APP}[-n].log'.
To view stdout while developing use 'make run | bunyan'.

### Installation
To install the bunyan CLI first install nodejs with '[sudo] yum install
nodejs'. Then install bunyan using '[sudo] npm i -g bunyan'.

## Updating dependencies
ONCE only: 'cp src/dev/settings.xml ~/.m2' to add Clojars and Artifactory
repo references. If you already have a ~/.m2/settings file, simply merge the
relevant 'server' and 'repository' sections from src/dev/settings.xml into
your existing ~/.m2/settings.xml file.

run 'make outdated-dev | outdated' to list 'out of date' dependencies.
run 'make update' to update out of date dependencies in deps.edn.

Or use your favorite editor to update the out of date dependencies in either ./deps.edn
or ${HOME}/.clojure/deps.edn. Be sure to sync the latter back to src/dev/deps.edn when
committing in git. The latter contains dependendencies used during development
only such as nRepl, static code/idiom analysis, testing, debugging, profiling, etc..
The former contains dependencies required solely by the uS you're building.

### Install Dependency Management Tools
Maven is required to install the generated uS-lib jar in the local repository.
To install maven manually, visit https://maven.apache.org/download.cgi and
download the latest version and un[zip|tar]. Ensure apache-maven's bin directory is
in your $PATH.

### Consuming jars from Artifactory
To consume jars from Artifactory (e.g. deps.edn) you need to complete the following steps:
   - Install the artifactory certificate in the cacerts file of your JDK.
      - See comments in script bin/add-cert-java for details.
   - Augment ~/.m2/settings.xml with artifactory repo details
      - See the artifactory repository section in src/dev/settings.xml for details.

If you have no ~/.m2/settings.xml file, simply copy src/dev/settings.xml to
~/.m2. If you intend to publish to artifactory using maven, update the artifactory
server section with your actual artifactory credentials. See section
"Publish to Artifactory" below. Else simply merge the artifactory server
(only when publishing using maven) and repository sections into your existing
~/.m2/settings file.

## Packaging for production
Choose one of the following methods to package for production:

- 'make bjar' to generate an uberjar built using clojure. All source files will be compiled to .class files
- 'make pkg' to generate a jar using bin/pkg. All sources files will be compiled to .class files.
- 'make uberjar' to generate a jar in the target directory. Most source files will be compiled to .class files

Once the uberjar bas been built, use the install target in the Makefile
corresponding to the build method used to create an uberjar of uS-lib to
install the jar in the local repository.

- make | make pkg | make uberjar -> make install
- make bjar -> make install-bjar

### Versioning of jars and repo tags
Jars are automatically versioned based on a tag combined
with a git revision resulting from 'git describe --tag --dirty'. Hence, your
repo requires at least one tag. If your cloned repo does not contain a tag,
create a tag at the first commit using 'git tag 0.0 sha-first-commit'. Be sure
to add tags to your repo as you release updates to production. To push tags
back to the original repo use 'git push --tags orgin'. If you work within a
git repo/workspace and don't have a tag, a '0.0' tag will be generated for
you on the first 'run' and will be associated with your first commit. If you
are not working in a git repo/workspace, a 'No-Git' pseudo tag will be
generated for you on the first 'run'.

### Publish to Artifactory
If you need to publish the final library jar and/or executable uberjar to artifactory,
the simplest approach is to run 'bin/publish-art'. You should have built the production jar
as described above. Before publishing to Artifactory you need to have setup your artifactory
credentials in ~/.artifactory-creds. Your artifactory credentials are most likely your
corporate credentials. Be sure to 'chmod go-rwx' this file. See 'bin/publish-art' for details.

If you intend to publish to artifactory using maven, update the artifactory
server section with your actual artifactory credentials in ~/.m2/settings.xml. See
src/dev/settings.xml for details. If you put your credentials in ~/.m2/settings.xml
be sure to read and follow the instructions described in the
*password encryption* URL referenced in the comments right above the server
section. Also, 'chmod go-rwx' ~/.m2/settings.xml.

## Invocation
To develop and work on the micro service (uS) jar:
   - 'make repl | nrepl | reply | rebel | rebl | run'
