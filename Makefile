# Target related variables
revision := $(shell git describe --tag --dirty)
app := $(shell basename $(CURDIR))
target-dir := target
jar-dir := $(target-dir)/lib
target := $(jar-dir)/$(app)-$(revision)

# Default clojure aliases
aliases := dev:test:badigeon

# Java to use
JAVA_HOME := $(shell /usr/libexec/java_home -v "Zulu 11")
#JAVA_HOME:=$(HOME)/tools/graalvm-ce-19.1.0/Contents/Home

# Source dependencies
sources := $(shell cd src/main/clj; find . -type f -name "*.clj")
classes := $(patsubst %.clj,$(target-dir)/.classes/%.class,$(sources))

.PHONY: compile pkg bjar uberjar jar debug-jar depstar capsule fmt fix-fmt lint kondo-init kondo yagni kibit analysis coverage outdated-dev outdated update install install-debug install-bjar pipeline clean real-clean

Default: $(target).jar

# When a class is out of date, remove it since bin/pkg | bin/compile will
# compile all outdated classes in one pass.
$(classes): $(target-dir)/.classes/%.class : src/main/clj/%.clj
	@rm -f $@

$(jar-dir):
	$(shell mkdir -p $(jar-dir))

compile: $(jar-dir) $(classes)
	bin/compile

# Jar building targets each using a different tool to build a jar:
$(target).jar: $(classes) deps.edn
	bin/pkg none

pkg: $(classes) deps.edn
	bin/pkg none

bjar:
	clojure -Abadigeon -m none -p jar

uberjar: $(jar-dir)
	clojure -A:uberjar -r src/resources --app-version $(revision) --out $(jar-dir)

jar:
	clojure -Ajar -r src/resources --app-version $(revision) --out $(jar-dir)

debug-jar: $(jar-dir)
	clojure -Auberdeps --target $(target)-debug.jar

depstar: $(jar-dir)
	clojure -A:depstar -m hf.depstar.uberjar $(target)-depstar.jar

capsule: $(jar-dir)
	clojure -A:pack mach.pack.alpha.capsule --application-id $(app) --application-version $(revision) -M "Built-By: $(USER)" -M "Build-Timestamp: $(date)" -M "Build-Host: $(shell uname -vn)" $(target)-capsule.jar

1jar: $(jar-dir)
	clojure -A:pack mach.pack.alpha.one-jar $(target)-1jar.jar


# Quality related invocations:
lint:
	clojure -Sverbose -Aeastwood

# https://github.com/borkdude/clj-kondo#project-setup
kondo-init:
	mkdir -p .clj-kondo
	clj-kondo --lint "$(shell clojure -Spath)"

kondo:
	clojure -Sverbose -Akondo

kibit:
	clojure -Sverbose -Akibit

analysis: lint kibit

coverage:
	clojure -Acloverage -p src/main/clj -t src/test/clj

outdated-dev:
	clojure -Sverbose -Adeps:outdated -a outdated,deps,find-deps,test,dev,nrepl,nrepl/old,pack,depstar,new,rebl,rebl8,standalone,proto,comp,rebel-clj,rebel-cljs,eastwood,kibit,uberdeps,jar,uberjar,badigeon,cloverage,fmt,liquid -t qualified,release

outdated:
	clojure -Sverbose -Aoutdated -a outdated,test,eastwood,kibit,uberdeps,uberjar,badigeon,cloverage,fmt -t release

update:
	clojure -Sverbose -Aoutdated --update

fmt:
	clojure -Afmt

fix-fmt:
	clojure -Afmt:fmt/fix

nvd:
	cat etc/nvd-tmpl.json | sed s/\"name\":\ /'&'\"$(app)\"/g | sed s/\"version\":\ /'&'\"$(revision)\"/g | sed s/\"classpath\":\ /'&'\[\"`clj -Spath | sed 's/:/\\\\",\\\\"/g' | sed 's,\/,\\\\/,g'`\"\]/g > nvd.json
	clojure -Anvd
	rm nvd.json


# Jar installation targets:
install: $(target).jar
	sed  '/^[ \t]*$$/d' pom.xml | sed "1,/<version>/ s/<version>.*<\/version>/<version>$(revision)<\/version>/" | sed "1,/<groupId>/ s/<groupId>.*<\/groupId>/<groupId>com.dell.spe.mres<\/groupId>/" > pom2.xml && mv pom2.xml pom.xml
	mvn install:install-file -Dfile=$(target).jar -DgroupId=com.dell.spe.mres -DartifactId=$(app) -Dversion=$(revision) -Dpackaging=jar -DpomFile=pom.xml

install-bjar:
	mvn install:install-file -Dfile=$(target).jar -DpomFile=pom.xml

install-capsule: $(target)-capsule.jar
	mvn install:install-file -Dfile=$(target)-capsule.jar -DgroupId=com.dell.spe.mres -DartifactId=$(app) -Dversion=$(revision)-capsule -Dpackaging=jar

install-debug: $(target)-debug.jar
	mvn install:install-file -Dfile=$(target)-debug.jar -DgroupId=com.dell.spe.mres -DartifactId=$(app) -Dversion=$(revision)-debug -Dpackaging=jar

validate-jenkinsfile:
	# export JENKINS_LINTER_URL=ssh://jenkins-user@jenkins-host:jenkins-port
	ssh $(JENKINS_LINTER_URL) declarative-linter < Jenkinsfile

# Jenkinsfile like build pipeline
pipeline: validate-jenkinsfile clean compile analysis bjar debug-jar fmt nvd outdated #pkg coverage install install-debug install-bjar


# Housekeeping targets:
clean:
	rm -rf $(target).jar $(classes)

real-clean:
	rm -rf $(target-dir) deploy
