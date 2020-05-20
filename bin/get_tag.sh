#!/bin/bash
set -ex

type=$(git branch | grep "\\*" | cut -d ' ' -f2 | cut -d '/' -f1)

if [ "$type" = "release" ] || [ "$type" = "hotfix" ] ; then
  git branch | grep "\\*" | cut -d ' ' -f2 | cut -d '/' -f2
elif [ "$type" = "master" ]; then
  echo "master"
elif git describe --contains 2> /dev/null; then
  exit 0
else
  echo "latest"
fi

