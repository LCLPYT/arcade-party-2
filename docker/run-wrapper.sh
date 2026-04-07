#!/usr/bin/env bash

# inspired in part by https://github.com/itzg/docker-minecraft-server/blob/master/scripts/start

set -e

: "${UID:=1000}"
: "${GID:=1000}"

umask "${UMASK:=0002}"

# start run.sh in usermode and fix permissions if needed
if [ "$(id -u)" = 0 ]; then
  runAsUser=minecraft
  runAsGroup=minecraft

  # change uid of minecraft user if needed
  if [[ -v UID ]]; then
    if [[ $UID != 0 ]]; then
      if [[ $UID != $(id -u minecraft) ]]; then
        echo "Changing uid of minecraft to $UID"
        usermod -u $UID minecraft
      fi
    else
      runAsUser=root
    fi
  fi

  # change gid of minecraft group if needed
  if [[ -v GID ]]; then
    if [[ $GID != 0 ]]; then
      if [[ $GID != $(id -g minecraft) ]]; then
        echo "Changing gid of minecraft to $GID"
        groupmod -o -g "$GID" minecraft
      fi
    else
      runAsGroup=root
    fi
  fi

  # chown if needed
  if [[ $(stat -c "%u" /template) != "$UID" ]]; then
    echo "Changing ownership of /template to $UID ..."
    chown -R ${runAsUser}:${runAsGroup} /template
  fi

  if [[ $(stat -c "%u" /data) != "$UID" ]]; then
    echo "Changing ownership of /data to $UID ..."
    chown -R ${runAsUser}:${runAsGroup} /data
  fi

  exec su-exec ${runAsUser}:${runAsGroup} "$@"
else
  exec "$@"
fi

