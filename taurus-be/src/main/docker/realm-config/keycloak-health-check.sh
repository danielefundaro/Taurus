#!/bin/bash
exec 3<>/dev/tcp/localhost/9990

# Keycloak answers only well-formed HTTP requests: CRLF line endings, and the connection closed after the response.
printf 'GET /health/ready HTTP/1.1\r\nHost: localhost:9990\r\nConnection: close\r\n\r\n' >&3

timeout --preserve-status 1 cat <&3 | grep -m 1 status | grep -m 1 UP
ERROR=$?

exec 3<&-
exec 3>&-

exit $ERROR
