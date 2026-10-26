# 使用 JRE 21 精简版基础镜像（daocloud 镜像源，国内拉取更快）
FROM m.daocloud.io/docker.io/eclipse-temurin:21-jre

ENV TZ=Asia/Shanghai
ENV LANG=en_US.UTF-8
ENV JAVA_XMS=256m
ENV JAVA_XMX=1024m

WORKDIR /root

COPY order.sh order.sh
COPY order-service/target/order-service.jar /root/order-service.jar
RUN chmod +x order.sh

# 业务端口 + Actuator 管理端口
EXPOSE 8100 8102
CMD ["/root/order.sh"]
