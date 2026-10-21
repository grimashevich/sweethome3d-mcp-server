#!/usr/bin/env bash
# e2e.sh — run the end-to-end tests against a live Sweet Home 3D.
#
# Builds the plugin, starts Sweet Home 3D (lib/SweetHome3D.jar) on a virtual X display with the
# plugin installed in a throw-away home directory, waits for the MCP server, runs the *LiveIT
# tests (Maven profile "e2e") and shuts everything down again. Your own home directory and your
# Sweet Home 3D settings are never touched.
#
# Usage:
#   bash scripts/e2e.sh [maven arguments...]
#   bash scripts/e2e.sh -Dit.test=WallsLiveIT        # a single test class
#
# Needs: bash, curl, Xvfb, a JDK 11+ and lib/SweetHome3D.jar (see scripts/setup-dev.sh).
#
# Environment:
#   SH3D_E2E_JAVA             java binary that runs Sweet Home 3D (default: $JAVA_HOME/bin/java, else java)
#   SH3D_E2E_JAVA_OPTS        extra JVM options for Sweet Home 3D, e.g. "-Duser.language=fr"
#   SH3D_E2E_PLUGIN           test this ready-made .sh3p instead of building one
#   SH3D_E2E_PORT             MCP server port (default: a free port is picked)
#   SH3D_E2E_STARTUP_TIMEOUT  seconds to wait for the MCP server to answer (default: 120)
#   SH3D_E2E_DISPLAY          use this X display instead of starting Xvfb, e.g. ":0" to watch the run
#   SH3D_E2E_TOKEN_FILE       file with the bearer token, passed to the tests as sh3d.e2e.tokenFile
#   SH3D_E2E_KEEP             set to 1 to keep the throw-away home directory after the run
#
# Logs: target/e2e-logs/ (Sweet Home 3D output, plugin log, Xvfb log), written when the run ends.
# Files written by the tests: target/e2e-work/.
# Exit code: the result of the tests; non-zero as well if Sweet Home 3D could not be started.

set -uo pipefail

PROJECT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
cd "$PROJECT_DIR" || exit 1

SH3D_JAR="$PROJECT_DIR/lib/SweetHome3D.jar"
LOG_DIR="$PROJECT_DIR/target/e2e-logs"
WORK_DIR="$PROJECT_DIR/target/e2e-work"
STARTUP_TIMEOUT=${SH3D_E2E_STARTUP_TIMEOUT:-120}

# Everything that lives only for this run: the throw-away home, java.io.tmpdir, the logs being written
RUN_DIR=$(mktemp -d "${TMPDIR:-/tmp}/sh3d-e2e.XXXXXX") || exit 1
RUN_LOGS="$RUN_DIR/logs"
HOME_DIR="$RUN_DIR/home"
mkdir -p "$RUN_LOGS" "$HOME_DIR/.eteks/sweethome3d/plugins" "$RUN_DIR/tmp"

SH3D_PID=""
XVFB_PID=""

log() {
  echo "[e2e] $*"
}

# Stops a process we started: asks nicely, then kills it. The Sweet Home 3D instance is disposable.
stop_process() {
  local pid=$1
  [ -n "$pid" ] || return 0
  kill -0 "$pid" 2>/dev/null || return 0
  kill "$pid" 2>/dev/null
  for _ in $(seq 1 20); do
    kill -0 "$pid" 2>/dev/null || return 0
    sleep 0.5
  done
  kill -9 "$pid" 2>/dev/null
  wait "$pid" 2>/dev/null
}

cleanup() {
  local status=$?
  trap - EXIT INT TERM
  stop_process "$SH3D_PID"
  stop_process "$XVFB_PID"
  # Collect the logs of this run, if it got as far as starting anything; the plugin writes its
  # own log next to the installed .sh3p
  if [ -n "$(ls -A "$RUN_LOGS" 2>/dev/null)" ]; then
    rm -rf "$LOG_DIR"
    mkdir -p "$LOG_DIR"
    cp "$RUN_LOGS"/* "$LOG_DIR"/ 2>/dev/null
    cp "$HOME_DIR"/.eteks/sweethome3d/plugins/sh3d-mcp.log* "$LOG_DIR"/ 2>/dev/null
    rm -f "$LOG_DIR"/*.lck
    log "logs: $LOG_DIR"
  fi
  if [ "${SH3D_E2E_KEEP:-0}" = "1" ]; then
    log "kept the temporary home: $HOME_DIR"
  else
    rm -rf "$RUN_DIR"
  fi
  exit "$status"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

die() {
  log "ERROR: $*" >&2
  if [ -s "$RUN_LOGS/sweethome3d.out" ]; then
    echo "----- last lines of sweethome3d.out -----" >&2
    tail -n 40 "$RUN_LOGS/sweethome3d.out" >&2
  fi
  exit 1
}

# Prints a TCP port on 127.0.0.1 that nothing listens on.
pick_free_port() {
  local port
  for _ in $(seq 1 100); do
    port=$((20000 + RANDOM % 12000))
    if ! (exec 3<>"/dev/tcp/127.0.0.1/$port") 2>/dev/null; then
      echo "$port"
      return 0
    fi
  done
  return 1
}

# ── Prerequisites ───────────────────────────────────────────────────────────
JAVA=${SH3D_E2E_JAVA:-}
if [ -z "$JAVA" ]; then
  if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
    JAVA="$JAVA_HOME/bin/java"
  else
    JAVA=java
  fi
fi
command -v "$JAVA" >/dev/null 2>&1 || die "java not found: $JAVA"
command -v curl >/dev/null 2>&1 || die "curl is required"
[ -f "$SH3D_JAR" ] || die "lib/SweetHome3D.jar is missing — run: bash scripts/setup-dev.sh"
if [ -z "${SH3D_E2E_DISPLAY:-}" ]; then
  command -v Xvfb >/dev/null 2>&1 || die "Xvfb is required (Debian/Ubuntu: sudo apt-get install xvfb)"
fi

# ── Plugin ──────────────────────────────────────────────────────────────────
PLUGIN=${SH3D_E2E_PLUGIN:-}
if [ -z "$PLUGIN" ]; then
  log "building the plugin"
  bash mvnw package -DskipTests --no-transfer-progress -q || die "the plugin build failed"
  PLUGIN=$(ls -t "$PROJECT_DIR"/target/sh3d-mcp-plugin-*.sh3p 2>/dev/null | head -n 1)
fi
if [ -z "$PLUGIN" ] || [ ! -f "$PLUGIN" ]; then
  die "plugin file not found: ${PLUGIN:-target/sh3d-mcp-plugin-*.sh3p}"
fi
log "plugin: $PLUGIN"

cp "$PLUGIN" "$HOME_DIR/.eteks/sweethome3d/plugins/" || die "cannot install the plugin into $HOME_DIR"
rm -rf "$WORK_DIR"

PORT=${SH3D_E2E_PORT:-}
if [ -z "$PORT" ]; then
  PORT=$(pick_free_port) || die "no free TCP port found"
fi

# ── X display ───────────────────────────────────────────────────────────────
if [ -n "${SH3D_E2E_DISPLAY:-}" ]; then
  DISPLAY_ID=$SH3D_E2E_DISPLAY
  # HOME is redirected below; keep the X authority of the real session reachable
  export XAUTHORITY=${XAUTHORITY:-$HOME/.Xauthority}
else
  # -displayfd lets Xvfb pick a free display number and report it
  Xvfb -displayfd 3 -screen 0 1280x1024x24 -nolisten tcp 3>"$RUN_DIR/display" >"$RUN_LOGS/xvfb.log" 2>&1 &
  XVFB_PID=$!
  for _ in $(seq 1 50); do
    [ -s "$RUN_DIR/display" ] && break
    kill -0 "$XVFB_PID" 2>/dev/null || die "Xvfb did not start: $(cat "$RUN_LOGS/xvfb.log")"
    sleep 0.2
  done
  [ -s "$RUN_DIR/display" ] || die "Xvfb did not report a display: $(cat "$RUN_LOGS/xvfb.log")"
  DISPLAY_ID=":$(cat "$RUN_DIR/display")"
fi

# ── Sweet Home 3D ───────────────────────────────────────────────────────────
# user.home (and HOME for native libraries) point to the throw-away directory: preferences, the
# plugins folder and the plugin's own config and log all live there. checkUpdates=false keeps the
# application off the network and stops the "new version" dialog. The executable jar unpacks
# Java 3D into java.io.tmpdir and opens the JDK packages it needs through its manifest.
log "starting Sweet Home 3D: $("$JAVA" -version 2>&1 | head -n 1), display $DISPLAY_ID, port $PORT"
# shellcheck disable=SC2086
env HOME="$HOME_DIR" DISPLAY="$DISPLAY_ID" "$JAVA" \
  -Duser.home="$HOME_DIR" \
  -Djava.io.tmpdir="$RUN_DIR/tmp" \
  -Dsh3d.mcp.port="$PORT" \
  -Dcom.eteks.sweethome3d.checkUpdates=false \
  ${SH3D_E2E_JAVA_OPTS:-} \
  -jar "$SH3D_JAR" >"$RUN_LOGS/sweethome3d.out" 2>&1 &
SH3D_PID=$!

deadline=$((SECONDS + STARTUP_TIMEOUT))
until curl -fsS -m 2 --noproxy '*' "http://127.0.0.1:$PORT/health" 2>/dev/null | grep -q '"status":"ok"'; do
  kill -0 "$SH3D_PID" 2>/dev/null || die "Sweet Home 3D exited during startup"
  [ "$SECONDS" -lt "$deadline" ] || die "the MCP server did not answer on port $PORT within ${STARTUP_TIMEOUT}s"
  sleep 1
done
kill -0 "$SH3D_PID" 2>/dev/null || die "port $PORT answers, but Sweet Home 3D is not running"
log "the MCP server is up"

# ── Tests ───────────────────────────────────────────────────────────────────
MVN_ARGS=(-Pe2e verify --no-transfer-progress "-Dsh3d.e2e.port=$PORT" "-Dsh3d.e2e.workDir=$WORK_DIR")
if [ -n "${SH3D_E2E_TOKEN_FILE:-}" ]; then
  MVN_ARGS+=("-Dsh3d.e2e.tokenFile=$SH3D_E2E_TOKEN_FILE")
fi
bash mvnw "${MVN_ARGS[@]}" "$@"
RESULT=$?

if ! kill -0 "$SH3D_PID" 2>/dev/null; then
  log "ERROR: Sweet Home 3D died during the tests" >&2
  [ "$RESULT" -ne 0 ] || RESULT=1
fi

if [ "$RESULT" -eq 0 ]; then
  log "PASSED"
else
  log "FAILED (exit code $RESULT)"
fi
exit "$RESULT"
