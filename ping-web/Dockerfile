# 前端静态资源镜像：dist 由 start.sh 在宿主机执行 npm run build 生成
FROM nginx:alpine

ENV TZ=Asia/Shanghai

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY dist/ /usr/share/nginx/html/

EXPOSE 80
