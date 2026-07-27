#!/bin/sh
# Minimal, JAR-free Maven Wrapper launcher.
set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
distribution_url=$(sed -n 's/^distributionUrl=//p' "$script_dir/.mvn/wrapper/maven-wrapper.properties")
maven_version=$(printf '%s\n' "$distribution_url" | sed -n 's#.*apache-maven/\([^/]*\)/.*#\1#p')
wrapper_home=${MAVEN_USER_HOME:-"$HOME/.m2"}/wrapper/dists/apache-maven-$maven_version
maven_cmd="$wrapper_home/apache-maven-$maven_version/bin/mvn"

if [ ! -x "$maven_cmd" ]; then
    archive="$wrapper_home/apache-maven-$maven_version-bin.zip"
    mkdir -p "$wrapper_home"
    if command -v curl >/dev/null 2>&1; then
        curl --fail --location --show-error --silent "$distribution_url" --output "$archive"
    elif command -v wget >/dev/null 2>&1; then
        wget -q "$distribution_url" -O "$archive"
    else
        echo "Error: curl or wget is required to download Maven." >&2
        exit 1
    fi
    if command -v unzip >/dev/null 2>&1; then
        unzip -q "$archive" -d "$wrapper_home"
    else
        echo "Error: unzip is required to unpack Maven." >&2
        exit 1
    fi
    rm -f "$archive"
fi

exec "$maven_cmd" "$@"
