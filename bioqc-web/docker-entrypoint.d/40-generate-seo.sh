#!/bin/sh
set -eu

SITE_URL="${PUBLIC_SITE_URL:-${RAILWAY_PUBLIC_DOMAIN:-}}"
API_URL="${APP_API_URL:-${VITE_API_URL:-}}"

if [ -n "${SITE_URL}" ] && [ "${SITE_URL#http}" = "${SITE_URL}" ]; then
  SITE_URL="https://${SITE_URL}"
fi

if [ -n "${API_URL}" ] && [ "${API_URL#http}" = "${API_URL}" ] && [ "${API_URL#/}" = "${API_URL}" ]; then
  API_URL="https://${API_URL}"
fi

if [ -z "${SITE_URL}" ]; then
  SITE_URL="http://localhost:${PORT:-3000}"
fi

export SEO_SITE_URL="${SITE_URL%/}"
export RUNTIME_API_URL="${API_URL%/}"

if [ -f /usr/share/nginx/html/config.js.template ]; then
  envsubst '${RUNTIME_API_URL} ${SEO_SITE_URL}' < /usr/share/nginx/html/config.js.template > /usr/share/nginx/html/config.js
fi

if [ -f /usr/share/nginx/html/robots.txt.template ]; then
  envsubst '${SEO_SITE_URL}' < /usr/share/nginx/html/robots.txt.template > /usr/share/nginx/html/robots.txt
fi

if [ -f /usr/share/nginx/html/sitemap.xml.template ]; then
  envsubst '${SEO_SITE_URL}' < /usr/share/nginx/html/sitemap.xml.template > /usr/share/nginx/html/sitemap.xml
fi
