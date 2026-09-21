#!/usr/bin/env bash
#
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.
#
# Air cloud environment startup for the Apache Maven source tree (3.2.2-SNAPSHOT).
#
# What this environment needs, and why:
#   * A JDK 8. The reactor compiles with -source/-target 1.6 (see the root pom.xml
#     properties), which modern JDKs refuse, so the image JDK (25) cannot build this
#     tree. A Temurin JDK 8 is installed under $HOME/.toolchains and becomes the
#     default JAVA_HOME for login and interactive shells.
#   * Ant, because README.md documents `ant` as the bootstrap path.
#   * Maven proxy configuration. All egress goes through the sandbox HTTP proxy;
#     Maven's resolver reads proxies from ~/.m2/settings.xml, not from the
#     HTTP(S)_PROXY variables, so the settings file is (re)generated on every boot
#     from the proxy currently advertised in the environment.
#
# The warm-up run additionally builds the whole reactor so that ~/.m2/repository and
# every module's target/ are already populated in the snapshot a real task boots from.

set -euo pipefail

log()  { printf '[startup] %s\n' "$*"; }
fail() { printf '[startup] ERROR: %s\n' "$*" >&2; exit 1; }

REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TOOLCHAINS_DIR="$HOME/.toolchains"
JDK_HOME="$TOOLCHAINS_DIR/jdk8"
ANT_VERSION="1.10.15"
ANT_DIR="$TOOLCHAINS_DIR/ant"
ENV_FILE="$HOME/.air-maven-env.sh"
MAVEN_SETTINGS="$HOME/.m2/settings.xml"
MARKER="air-maven-env"

# The launch tells us which mode we are in; never infer it from the process tree.
if [ "${AIR_STARTUP_MODE:-}" = warmup ]; then WARMUP=1; else WARMUP=; fi
log "mode=${AIR_STARTUP_MODE:-task} repo=$REPO_DIR"

# --------------------------------------------------------------------------------
# toolchains
# --------------------------------------------------------------------------------

fetch() { # fetch <url> <dest>
  local url="$1" dest="$2" attempt
  for attempt in 1 2 3; do
    if curl -fsSL --connect-timeout 20 -o "$dest" "$url"; then
      return 0
    fi
    log "download attempt $attempt failed: $url"
    sleep 5
  done
  return 1
}

install_jdk8() {
  if [ -x "$JDK_HOME/bin/javac" ]; then
    log "JDK 8 already installed: $("$JDK_HOME/bin/java" -version 2>&1 | head -1)"
    return
  fi
  log "installing Temurin JDK 8 into $JDK_HOME (needed for -source 1.6) ..."
  local tmp
  tmp="$(mktemp -d)"
  fetch "https://api.adoptium.net/v3/binary/latest/8/ga/linux/x64/jdk/hotspot/normal/eclipse?project=jdk" \
        "$tmp/jdk8.tar.gz" || fail "could not download Temurin JDK 8 from api.adoptium.net"
  tar -xzf "$tmp/jdk8.tar.gz" -C "$tmp"
  local extracted
  extracted="$(find "$tmp" -mindepth 1 -maxdepth 1 -type d -name 'jdk8*' | head -1)"
  [ -n "$extracted" ] || fail "unexpected layout in the Temurin JDK 8 archive"
  mkdir -p "$TOOLCHAINS_DIR"
  rm -rf "$JDK_HOME"
  mv "$extracted" "$JDK_HOME"
  rm -rf "$tmp"
  log "installed $("$JDK_HOME/bin/java" -version 2>&1 | head -1)"
}

install_ant() {
  if [ -x "$ANT_DIR/bin/ant" ]; then
    log "Ant already installed: $("$ANT_DIR/bin/ant" -version 2>/dev/null | head -1)"
    return
  fi
  log "installing Apache Ant $ANT_VERSION into $ANT_DIR (README bootstrap path) ..."
  local tmp
  tmp="$(mktemp -d)"
  fetch "https://archive.apache.org/dist/ant/binaries/apache-ant-${ANT_VERSION}-bin.tar.gz" \
        "$tmp/ant.tar.gz" || fail "could not download Apache Ant $ANT_VERSION"
  tar -xzf "$tmp/ant.tar.gz" -C "$tmp"
  mkdir -p "$TOOLCHAINS_DIR"
  rm -rf "$ANT_DIR"
  mv "$tmp/apache-ant-${ANT_VERSION}" "$ANT_DIR"
  rm -rf "$tmp"
  log "installed $(JAVA_HOME="$JDK_HOME" "$ANT_DIR/bin/ant" -version 2>/dev/null | head -1)"
}

install_jdk8
install_ant

export JAVA_HOME="$JDK_HOME"
export ANT_HOME="$ANT_DIR"
export PATH="$JAVA_HOME/bin:$ANT_HOME/bin:$PATH"

# --------------------------------------------------------------------------------
# shell environment: this script is a child process, so exports here die with it.
# Write them to a file and source that file from the login/interactive shell hooks.
# --------------------------------------------------------------------------------

proxy_url="${HTTPS_PROXY:-${https_proxy:-${HTTP_PROXY:-${http_proxy:-}}}}"
proxy_host=""
proxy_port=""
if [ -n "$proxy_url" ]; then
  proxy_hostport="${proxy_url#*://}"
  proxy_hostport="${proxy_hostport%%/*}"
  proxy_host="${proxy_hostport%%:*}"
  proxy_port="${proxy_hostport##*:}"
  [ "$proxy_port" = "$proxy_host" ] && proxy_port="3128"
  log "outbound proxy detected: $proxy_host:$proxy_port"
else
  log "no HTTP(S)_PROXY in the environment; assuming direct egress"
fi

{
  echo "# ${MARKER}: generated by .air/cloud/startup.sh, regenerated on every boot"
  echo "export JAVA_HOME=\"$JDK_HOME\""
  echo "export ANT_HOME=\"$ANT_DIR\""
  echo "export PATH=\"\$JAVA_HOME/bin:\$ANT_HOME/bin:\$PATH\""
  if [ -n "$proxy_host" ]; then
    # Plugins that open plain URLConnections (e.g. maven-remote-resources) need the
    # proxy as JVM system properties; the resolver itself reads ~/.m2/settings.xml.
    echo "export MAVEN_OPTS=\"\${MAVEN_OPTS:-} -Dhttp.proxyHost=$proxy_host -Dhttp.proxyPort=$proxy_port -Dhttps.proxyHost=$proxy_host -Dhttps.proxyPort=$proxy_port -Dhttp.nonProxyHosts=localhost|127.0.0.1\""
  fi
} > "$ENV_FILE"

hook_line=". \"\$HOME/$(basename "$ENV_FILE")\"  # ${MARKER}"

login_profile=""
for candidate in "$HOME/.bash_profile" "$HOME/.bash_login" "$HOME/.profile"; do
  if [ -f "$candidate" ]; then login_profile="$candidate"; break; fi
done
[ -n "$login_profile" ] || { login_profile="$HOME/.profile"; : > "$login_profile"; }

for rc in "$login_profile" "$HOME/.bashrc"; do
  [ -f "$rc" ] || : > "$rc"
  if ! grep -qF "$MARKER" "$rc"; then
    printf '\n%s\n' "$hook_line" >> "$rc"
    log "hooked $ENV_FILE into $rc"
  fi
done

# --------------------------------------------------------------------------------
# Maven settings: proxy + the one build workaround this environment needs.
# --------------------------------------------------------------------------------

write_maven_settings() {
  mkdir -p "$HOME/.m2"
  if [ -f "$MAVEN_SETTINGS" ] && ! grep -qF "$MARKER" "$MAVEN_SETTINGS"; then
    log "WARNING: $MAVEN_SETTINGS exists and was not generated here - leaving it alone."
    log "WARNING: it must carry the sandbox proxy itself or Maven downloads will fail."
    return
  fi

  local proxies=""
  if [ -n "$proxy_host" ]; then
    proxies="  <proxies>
    <proxy><id>air-http</id><active>true</active><protocol>http</protocol><host>${proxy_host}</host><port>${proxy_port}</port><nonProxyHosts>localhost|127.0.0.1</nonProxyHosts></proxy>
    <proxy><id>air-https</id><active>true</active><protocol>https</protocol><host>${proxy_host}</host><port>${proxy_port}</port><nonProxyHosts>localhost|127.0.0.1</nonProxyHosts></proxy>
  </proxies>
"
  fi

  cat > "$MAVEN_SETTINGS" <<EOF
<!-- ${MARKER}: generated by .air/cloud/startup.sh, regenerated on every boot -->
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0">
${proxies}  <profiles>
    <profile>
      <id>air-defaults</id>
      <properties>
        <!--
          The apache-maven distribution module renders its LICENSE through
          maven-remote-resources-plugin, which downloads
          https://glassfish.java.net/public/CDDLv1.0.html - a host that has not
          existed since java.net was decommissioned, so the module can never
          build with it enabled. Skipping the goal is an environment-level
          workaround for the dead URL; drop this property if the build is ever
          pointed at a live license URL.
        -->
        <remoteresources.skip>true</remoteresources.skip>
      </properties>
    </profile>
  </profiles>
  <activeProfiles>
    <activeProfile>air-defaults</activeProfile>
  </activeProfiles>
</settings>
EOF
  log "wrote $MAVEN_SETTINGS"
}

write_maven_settings

# --------------------------------------------------------------------------------
# warm-up build: primes ~/.m2/repository and every module's target/ for the snapshot
# --------------------------------------------------------------------------------

warmup_build() {
  cd "$REPO_DIR"

  log "building the full reactor (tests skipped) - this populates ~/.m2 and target/ ..."
  if ! mvn -B -Dmaven.test.skip=true clean install; then
    fail "the reactor build failed; see the output above"
  fi
  log "reactor build finished"

  # Second pass: actually run the tests. This caches the test-scope dependencies and
  # the surefire providers, which surefire resolves only when it runs - without this
  # pass a later offline `mvn test` cannot even start.
  # It is expected to fail in maven-core, whose test sources do not compile against the
  # current main sources (DefaultLifecycleTaskSegmentCalculatorTest references
  # MavenProject.setDefaultGoal, which does not exist). That is a property of the
  # checked-in code, not of this environment, so the pass is advisory only.
  log "running the test suites to prime test-scope deps and surefire providers"
  log "(failures here are advisory and do not fail startup) ..."
  if mvn -B -fae test; then
    log "all test suites passed"
  else
    log "NOTE: some modules did not build or test cleanly (known: maven-core test"
    log "NOTE: sources reference MavenProject.setDefaultGoal, which main does not define)."
  fi
}

# --------------------------------------------------------------------------------
# healthcheck: prove a real task can actually build and test this tree
# --------------------------------------------------------------------------------

healthcheck() {
  cd "$REPO_DIR"

  log "healthcheck: java version"
  local java_version
  java_version="$("$JDK_HOME/bin/java" -version 2>&1 | head -1)"
  case "$java_version" in
    *1.8.0*) log "healthcheck: $java_version" ;;
    *) fail "healthcheck: expected a JDK 8 at $JDK_HOME, got: $java_version" ;;
  esac

  log "healthcheck: maven runs on that JDK"
  mvn -version || fail "healthcheck: mvn -version failed"

  log "healthcheck: ant is available"
  ant -version || fail "healthcheck: ant -version failed"

  # Offline on purpose: it fails unless the warm-up really cached everything the
  # build needs, which is exactly what the snapshot is supposed to carry.
  log "healthcheck: offline unit-test smoke (maven-artifact, maven-model) ..."
  local smoke_log
  smoke_log="$(mktemp)"
  if mvn -B -o -pl maven-artifact,maven-model test > "$smoke_log" 2>&1; then
    grep -E '^Tests run:' "$smoke_log" | tail -2 | sed 's/^/[startup] healthcheck: /'
  else
    tail -40 "$smoke_log" >&2
    fail "healthcheck: offline unit tests failed (see output above)"
  fi
  rm -f "$smoke_log"

  # The end product of this repository is a runnable Maven distribution: unpack the
  # one the warm-up just built and make it report its own version.
  log "healthcheck: the freshly built Maven distribution starts"
  local dist_archive dist_dir
  dist_archive="$(find "$REPO_DIR/apache-maven/target" -maxdepth 1 -name 'apache-maven-*-bin.tar.gz' | head -1)"
  [ -n "$dist_archive" ] || fail "healthcheck: no distribution archive in apache-maven/target"
  dist_dir="$(mktemp -d)"
  tar -xzf "$dist_archive" -C "$dist_dir"
  local dist_version
  dist_version="$("$dist_dir"/apache-maven-*/bin/mvn -version 2>&1 | head -1)" \
    || fail "healthcheck: the built Maven distribution failed to start"
  rm -rf "$dist_dir"
  case "$dist_version" in
    "Apache Maven 3.2.2-SNAPSHOT"*) log "healthcheck: $dist_version" ;;
    *) fail "healthcheck: unexpected version from the built distribution: $dist_version" ;;
  esac

  log "healthcheck: OK"
}

if [ -n "$WARMUP" ]; then
  warmup_build
  healthcheck
else
  # Real task run: the snapshot already carries the toolchains, ~/.m2 and target/,
  # so return immediately and let the task drive the build itself.
  log "task mode: toolchains and Maven settings are in place; skipping the warm-up build"
fi

log "done"
