#!/usr/bin/env bash
set -euo pipefail

bundle=$(cd "$(dirname "$0")/.." && pwd)
artifacts=${3:?release archive directory required}
export RELEASE_SHA=${1:?commit SHA required}
[[ $RELEASE_SHA =~ ^[0-9a-f]{40}$ ]]
run_number=${2:?workflow run number required}
[[ $run_number =~ ^[1-9][0-9]*$ ]]
export DEPLOY_ROOT=${DEPLOY_ROOT:-/srv/b2b-it-marketplace/deploy}
: "${MARKETPLACE_SECRETS_DIR:?Set the absolute path to the stand database secrets}"
export MARKETPLACE_PORTFOLIO_DIR=${MARKETPLACE_PORTFOLIO_DIR:-/srv/b2b-it-marketplace/portfolio}
[[ -d $MARKETPLACE_PORTFOLIO_DIR ]]
umask 022
exec 9>"$DEPLOY_ROOT/deploy.lock"
flock 9
cd "$DEPLOY_ROOT"
compose=(docker compose -f "$bundle/compose.stand.yaml")
web() { "${compose[@]}" exec -T web "$@"; }
workers() { web ps -o pid,args | awk '/nginx: worker process/ {print $1}'; }
wait_for_workers() {
    local retired=$1 remaining attempt
    for ((attempt=0; attempt<120; attempt++)); do
        remaining=$(workers) || return 1
        if [[ -n $remaining ]] && ! grep -Fxqf <(echo "$retired") <<<"$remaining"; then
            return 0
        fi
        sleep 1
    done
    echo "Nginx worker turnover timed out; both backends remain running" >&2
    return 1
}
point_to() {
    ln -sfn "$1" routes/.next
    mv -Tf routes/.next routes/active.conf
}
previous=$(readlink routes/active.conf)
[[ $previous =~ ^([1-9][0-9]*)-(blue|green)-[0-9a-f]{40}\.site$ ]]
previous_run=${BASH_REMATCH[1]}
previous_color=${BASH_REMATCH[2]}
if ((run_number < previous_run)); then
    echo "A newer run has already been deployed"
    exit 0
fi
# Compose mounts the signing key by filename when it creates the candidate.
docker run --rm -i --network none --mount "type=bind,src=$MARKETPLACE_SECRETS_DIR,dst=/secrets" \
    --entrypoint sh postgres:17-alpine < "$bundle/ci/init-jwt.sh"
color=blue
if [[ $previous_color == blue ]]; then color=green; fi
service=backend-$color
route=$run_number-$color-$RELEASE_SHA.site

# Recover an interrupted switch before reusing a possibly still referenced slot.
if [[ -n $("${compose[@]}" ps -q --status running "$service") ]]; then
    before=$(workers)
    [[ -n $before ]]
    web nginx -t
    web nginx -s reload
    wait_for_workers "$before"
fi

docker load -i "$artifacts/backend-$RELEASE_SHA.tar.gz"
docker image inspect "marketplace-backend:$RELEASE_SHA" >/dev/null
if [[ ! -d static/$RELEASE_SHA ]]; then
    stage=$(mktemp -d static/.unpack.XXXXXX)
    tar --no-same-owner --no-same-permissions -xzf "$artifacts/frontend-$RELEASE_SHA.tar.gz" -C "$stage"
    test -s "$stage/index.html"
    chmod -R a+rX "$stage"
    mv "$stage" "static/$RELEASE_SHA"
fi

"${compose[@]}" up -d --no-build --no-deps "$service"
candidate=$("${compose[@]}" ps -aq "$service")
ready=false
for ((attempt=0; attempt<60; attempt++)); do
    if web wget -T 2 -qO /dev/null \
        "http://$service:8080/actuator/health/readiness"; then
        ready=true
        break
    fi
    if [[ $(docker inspect -f '{{.RestartCount}}' "$candidate") != 0 ]]; then
        break
    fi
    sleep 2
done
if [[ $ready != true ]]; then
    "${compose[@]}" logs --tail 120 "$service" >&2
    "${compose[@]}" stop "$service"
    exit 1
fi

cat >"routes/$route" <<EOF
server {
    listen 80;
    root /srv/releases/$RELEASE_SHA;
    location /api/ {
        resolver 127.0.0.11 valid=10s;
        set \$spring $service:8080;
        proxy_pass http://\$spring;
    }
    location / { try_files \$uri \$uri/ /index.html; }
}
EOF
old_workers=$(workers)
[[ -n $old_workers ]]
point_to "$route"
if ! web nginx -t || ! web nginx -s reload; then
    point_to "$previous"
    exit 1
fi
# A successful signal alone does not prove that the master adopted the config.
wait_for_workers "$old_workers"
"${compose[@]}" stop "backend-$previous_color"
