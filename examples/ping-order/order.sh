#!/bin/bash

APP_NAME=order-service.jar

# JVM 参数：时区、堆内存（通过环境变量覆盖，默认 512m~1024m）
JAVA_OPTS="${JAVA_OPTS} -Duser.timezone=Asia/Shanghai"
JAVA_OPTS="${JAVA_OPTS} -Xms${JAVA_XMS:-512m}"
JAVA_OPTS="${JAVA_OPTS} -Xmx${JAVA_XMX:-1024m}"

exec java ${JAVA_OPTS} -jar ${APP_NAME}