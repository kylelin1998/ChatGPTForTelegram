FROM elderbyte/docker-alpine-jdk8-ffmpeg
ENV TZ=Asia/Shanghai
RUN apk add --no-cache ffmpeg
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone
RUN sed -i 's/dl-cdn.alpinelinux.org/mirrors.aliyun.com/g' /etc/apk/repositories
# 增加字体，解决验证码没有字体报空指针问题
RUN set -xe \
&& apk --no-cache add ttf-dejavu fontconfig
# 系统编码
ENV LANG=C.UTF-8 LC_ALL=C.UTF-8
COPY ChatGPTForTelegram-universal.jar app.jar
# 启动jar包
ENTRYPOINT ["java","-Djava.security.egd=file:/dev/./urandom","-jar","/app.jar"]