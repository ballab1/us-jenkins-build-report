#!/bin/bash

#----------------------------------------------------------------------------
### Installation of content needed by OS
function createRootScript() {
  local -r root_script="${1:?}"
  #>> Create script to setup container >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
  cat > "$root_script" << EOF
#!/bin/bash

# install needed package suppport
apt update
apt install -y curl make openjdk-11-jdk vim git maven gradle nodejs npm rlwrap
npm install -g bunyan

export JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64
cd /opt
bin/install-clojure

# add user so we can run build with same UID:GID inside & outside docker environment
addgroup -gid "$d_GID" "$d_NAME"
adduser --disabled-password    \
        --shell /bin/bash      \
        --uid "$d_UID"         \
        --gid "$d_GID"         \
        "$d_NAME" < <(





)

EOF
  #<< End of script to setup container <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<
  chmod a+x "$root_script"
}

#----------------------------------------------------------------------------
function createUserScript() {
  local -r user_script="${1:?}"
  #>> Create USER script to build uS-lib project >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
cat > "$user_script" << 'EOF'
#!/bin/bash

# user environment for build
export JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64
echo "JAVA_HOME: $JAVA_HOME"
export HOME="$(getent passwd $UID | cut -d ':' -f 6)"
export GRADLE_USER_HOME="$HOME"
export USER_HOME="$HOME"
PACKAGE_TYPE=bjar
base_dir=/opt

# load support functions
source "${base_dir}/etc/utils.shlib"

# workaround to fix 'function not found' from some of build
while read -r funct; do
    export -f "${funct?}"
done < <(grep 'function' "${base_dir}/etc/utils.shlib" | sed -E 's|^function (.+)\(.+$|\1|')

# workaround 'rlwrap' bug #28009  (https://github.com/moby/moby/issues/28009)
{ stty -a;stty -a;sleep 0.01;stty -a; } > /dev/null 2>&1


# change to directory mounted on us-Lib project
cd "${base_dir}" ||:

# make sure our HOME dir is set up
[ -d ~/.clojure ] || mkdir -p ~/.clojure
[ -f ~/.clojure/deps.edn ] || cp "${base_dir}/src/dev/deps.edn" ~/.clojure/
[ -d ~/.m2 ] || mkdir -p ~/.m2
[ -f ~/.m2/settings.xml ] || cp "${base_dir}/src/dev/settings.xml" ~/.m2/

# perform dependecy checks  (one time)
echo
echo '-- bin/dcheck ------------------------------------------------------------------------------'
bin/dcheck
echo
echo '-- bin/dcheck-mvn --------------------------------------------------------------------------'
bin/dcheck-mvn

# update environment
echo
echo '-- make outdated-dev -----------------------------------------------------------------------'
make outdated-dev
echo
echo '-- make outdated ---------------------------------------------------------------------------'
make outdated


# perform build
case "$PACKAGE_TYPE" in
  bjar)
    echo
    echo '-- make bjar -------------------------------------------------------------------------------'
    make bjar
    echo
    echo '-- make install-bjar -----------------------------------------------------------------------'
    make install-bjar
    ;;

  pkg)
    echo
    echo '-- make ------------------------------------------------------------------------------------'
    make
    echo
    echo '-- make pkg --------------------------------------------------------------------------------'
    make pkg
    echo
    echo '-- make uberjar ----------------------------------------------------------------------------'
    make uberjar
    ;;

  uberjar)
    echo
    echo '-- make ------------------------------------------------------------------------------------'
    make
    echo
    echo '-- make pkg --------------------------------------------------------------------------------'
    make pkg
    echo
    echo '-- make uberjar ----------------------------------------------------------------------------'
    make uberjar
    ;;
esac

EOF
  #<< End of USER script to build uS-lib project <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<
  chmod a+x "$user_script"
}

#----------------------------------------------------------------------------
function scrubLog() {
  local -r logFile="${1:?}"
  #sed -E -i -e  's#\x1B\[([0-9]{1,2}(;[0-9]{1,2})?)?[mGK]|\r|\x1b\[\?25l|\x1b\[25h|\x1B\[1A##g' -e 's|^\s+$||g' -e 's|\n\n|\n|g' "$logFile"
}

#############################################################################
#
#        MAIN
#
#############################################################################

declare -r d_UID=$(id -u)
declare -r d_GID=$(id -g)
declare -r d_NAME=a_user

declare -r CONTAINER_NAME=ubuntu
declare -r IMAGE_NAME=ubuntu:latest

declare -r ROOT_LOG=root.log
declare -r ROOT_SCRIPT=.stash/root.sh
declare -r USER_LOG=user.log
declare -r USER_SCRIPT=.stash/user.sh


# cleanup from any pior runs to ensure fresh/clean start
if [ $(docker ps -a | grep -c "$CONTAINER_NAME") -gt 0 ]; then
    docker stop "$CONTAINER_NAME"
    docker rm "$CONTAINER_NAME"
fi
[ -d target ] && rm -rf target
[ -f "$ROOT_LOG" ] && rm "$ROOT_LOG"
[ -f "$USER_LOG" ] && rm "$USER_LOG"


# create our scripts
[ -d .stash ] || mkdir .stash
createRootScript "$ROOT_SCRIPT"
createUserScript "$USER_SCRIPT"

# start new environment and run our scripts
docker run -d -it --name "$CONTAINER_NAME" -u root --volume "$(pwd):/opt" --entrypoint cat "$IMAGE_NAME"
docker exec -t --user root "$CONTAINER_NAME" "/opt/$ROOT_SCRIPT" &> "$ROOT_LOG"
docker exec -t --user "$d_NAME" "$CONTAINER_NAME" "/opt/$USER_SCRIPT" &> "$USER_LOG"

# cleanup the logs
scrubLog "$ROOT_LOG"
scrubLog "$USER_LOG"

# make sure everything is accessible & cleanup
for f in "$USER_LOG" "$USER_SCRIPT" "$ROOT_LOG" "$ROOT_SCRIPT"; do
    [ -f $f ] || continue
    sudo chown $(id -u):$(id -g) $f
    sudo chmod a+rw $f
#    rm -f $f
done
#docker stop ubuntu
#docker rm ubuntu
