#!/bin/bash

APP_NAME=gateway-service.jar

# JVM 参数
JAVA_OPTS="${JAVA_OPTS} -Duser.timezone=Asia/Shanghai"
JAVA_OPTS="${JAVA_OPTS} -Xms${JAVA_XMS:-256m}"
JAVA_OPTS="${JAVA_OPTS} -Xmx${JAVA_XMX:-1024m}"

exec java ${JAVA_OPTS} -jar ${APP_NAME}
