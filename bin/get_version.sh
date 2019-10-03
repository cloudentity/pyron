#!/bin/bash

set -e

mvn -q -Dexec.executable="echo" -Dexec.args='${project.version}' --non-recursive exec:exec
