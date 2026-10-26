# 使用 JRE 21 精简版基础镜像（daocloud 镜像源，国内拉取更快）
FROM m.daocloud.io/docker.io/eclipse-temurin:21-jre

ENV TZ=Asia/Shanghai
ENV LANG=en_US.UTF-8
ENV JAVA_XMS=256m
ENV JAVA_XMX=1024m

WORKDIR /root

COPY user-service.sh user-service.sh
COPY user-service/target/user-service.jar /root/user-service.jar
RUN chmod +x user-service.sh

# 业务端口 + Actuator 管理端口
EXPOSE 8099 8101
CMD ["/root/user-service.sh"]
