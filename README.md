### 简体中文 | [English](./README_en.md)

![License](https://img.shields.io/badge/license-MIT-green)
[![release](https://img.shields.io/github/v/release/kylelin1998/ChatGPTForTelegram)](https://github.com/kylelin1998/ChatGPTForTelegram/releases/latest)

## 介绍
- Youtube： https://youtu.be/a_WLFtRWGzY
- 哔哩哔哩： https://www.bilibili.com/video/BV1qX4y1z7Dt/

ChatGPT机器人， 这是一个开源项目， 你可以基于它搭建属于自己的机器人

使用机器人可以让你轻松进行对话， 后续机器人的更新升级一个命令即可搞定， 无需再上服务器进行升级机器人

1. 支持各种场景文本对话
2. 语音对话
3. 录制重放对话
4. 支持生成图片

你可以事先录制一段对话， 后面用到的时候直接快速重放出来， 就不用每次建立新对话就要发送一堆引导文本了

## 最近更新日志
开源ChatGPT TG机器人 v1.0.50 更新说明视频
- ⭐ Youtube: https://youtu.be/9hczaDzOvGA
- ⭐ 哔哩哔哩： https://www.bilibili.com/video/BV1wV41137Yf/

1. 支持多ChatGPT Api Key， 随机切换使用， 死号自动通知管理员
2. 支持精简回复， 开启后部分命令会去除回复的问题显示和退出提示， 仅显示回复内容
3. 超级加强引导， 录制重放对话支持替换变量， 直接发送文本即可， 无需再增加文本来引导

## 部署
机器人的部署步骤是基于 Docker 的，其机器人升级功能也基于 Docker，因此请使用 Docker 进行部署，以防出现错误

### 部署方式1 (推荐)
- ⭐ Youtube: https://youtu.be/mNg6TFyozZk
- ⭐ 哔哩哔哩： https://www.bilibili.com/video/BV1qF411f7pg/

#### 一键部署
```
docker run --name gpttb -d -v $(pwd)/config:/app/config -e GPT_TOKEN=你的GPTApiKey -e BOT_ADMIN_ID=管理者的ChatId -e BOT_NAME=机器人的username -e BOT_TOKEN=机器人token --restart=always kylelin1998/chatgpt-tg-bot
```
#### 一键部署(开启代理)
```
docker run --name gpttb -d -v $(pwd)/config:/app/config -e GPT_TOKEN=你的GPTApiKey -e BOT_ADMIN_ID=管理者的ChatId -e BOT_NAME=机器人的username -e BOT_TOKEN=机器人token -e PROXY=true -e PROXY_HOST=127.0.0.1 -e PROXY_PORT=7890 --restart=always kylelin1998/chatgpt-tg-bot
```

### 部署方式2 (不推荐)
- Youtube：https://youtu.be/CiDxb1ESijQ
- 哔哩哔哩： https://www.bilibili.com/video/BV1Ts4y1S7bn/

### 准备
![ff3379f00b462db7b016f361c9b8fb7cd9097dc8.png](https://openimg.kylelin1998.com/img/ff3379f00b462db7b016f361c9b8fb7cd9097dc8.png)

首先，在您的服务器上创建一个文件夹

然后，在其中创建名为 config 的另一个文件夹，config文件夹下必须包含名为 config.json 的JSON文件

接着，将 ChatGPTForTelegram-universal.jar, run.sh 和 Dockerfile 传输到该文件夹中

### config.json
```
{
  "open": false,
  "on_proxy": false,
  "proxy_host": "127.0.0.1",
  "proxy_port": 7890,
  "bot_admin_id": "xxxx",
  "bot_name": "xxx_bot",
  "bot_token": "xxxx",
  "debug": false,
  "gpt_token": "xxxxx",
  "gpt_model": "gpt-3.5-turbo",
  "permission_chat_id_array": [
    "xxxx"
  ],
  "block_chat_id_array": []
}
```
```
open -> 
开放状态：任何人都可以使用这个机器人。
关闭状态：只有permission chat id列表才能使用这个bot。
on proxy 代表是否开启代理
bot admin id 就是你要指定那个号是管理员， 这个id是chat id
bot name, 和 bot token 就是机器人创建好就有的，你肯定知道
gpt token -> 就是 gpt的token
permission chat id array -> 这个就是代表你只能允许列表下的这些chat id使用机器人， 可以填写个人的，或者是群的chat id
block_chat_id_array -> 不允许使用机器人的chat id列表
```

### 第一步:
编译镜像
```
docker build -t gptft .
```

### 第二步:
运行容器镜像
```
docker run --name gptft -d -v $(pwd):/app --restart=always gptft
```

## 使用
机器人命令:
```
chat - 发起对话
c - 发起对话
ask - 单次提问
a - 单次提问
cml - 发起消息限制对话
nccm - 发起无上下文对话
image - 生成图片
record - 录制对话
p - 重放对话
record_list - 录制对话管理
exit - 退出
language - 切换语言
admin - 管理员命令
restart - 重启机器人
upgrade - 升级机器人
help - 帮助
```
![560c3fe7450239da5ad0d9638cfd4fd66551d576.png](https://openimg.kylelin1998.com/img/560c3fe7450239da5ad0d9638cfd4fd66551d576.png)
![9d57081a0e248f157e427618c3430a44f3b1785d.png](https://openimg.kylelin1998.com/img/9d57081a0e248f157e427618c3430a44f3b1785d.png)
