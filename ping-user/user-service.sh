#!/bin/bash

#这里可替换为你自己的执行程序，其他代码无需更改

APP_NAME=user-service.jar

# 支持通过环境变量注入 JVM 参数
JAVA_OPTS="${JAVA_OPTS} -Duser.timezone=Asia/Shanghai"
JAVA_OPTS="${JAVA_OPTS} -Xms${JAVA_XMS:-256m}"
JAVA_OPTS="${JAVA_OPTS} -Xmx${JAVA_XMX:-1024m}"

#使用说明，用来提示输入参数
exec java ${JAVA_OPTS} -jar ${APP_NAME}