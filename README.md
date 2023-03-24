### 简体中文 | [English](./README_en.md)

![License](https://img.shields.io/badge/license-MIT-green)
[![release](https://img.shields.io/github/v/release/kylelin1998/ChatGPTForTelegram)](https://github.com/kylelin1998/ChatGPTForTelegram/releases/latest)

## 介绍
目前机器人基于gpt-3.5-turbo
使用机器人可以让你轻松进行对话， 后续机器人的更新升级一个命令即可搞定， 无需再上服务器进行升级机器人

## 部署
机器人的部署步骤是基于 Docker 的，其机器人升级功能也基于 Docker，因此请使用 Docker 进行部署，以防出现错误

### Prepare
![ff3379f00b462db7b016f361c9b8fb7cd9097dc8.png](https://openimg.kylelin1998.com/img/ff3379f00b462db7b016f361c9b8fb7cd9097dc8.png)

首先，在您的服务器上创建一个你喜欢的名称的文件夹
然后，在其中创建名为“config”的另一个文件夹，“config”文件夹必须包含名为“config.json”的JSON文件
接着，将“ChatGPTForTelegram-universal.jar”和“Dockerfile”传输到该文件夹中

### config.json
```
{
  "on_proxy": false,
  "proxy_host": "127.0.0.1",
  "proxy_port": 7890,
  "bot_admin_id": "xxxx",
  "bot_name": "xxx_bot",
  "bot_token": "xxxx",
  "debug": false,
  "gpt_token": "xxxxx",
  "permission_chat_id_array": [
    "xxxx"
  ]
}
```

### 第一步:
编译镜像
```
docker build -t gptft .
```

### 第二步:
运行容器镜像
```
docker run --name gptft -d -v $(pwd)/logs:/logs -v $(pwd)/ChatGPTForTelegram-universal.jar:/app.jar -v $(pwd)/config:/config  --restart=always gptft
```

## 关于我
我的TG: https://t.me/KyleLin1998
我的TG频道: https://t.me/KyleLin1998Channel
我的邮箱: email@kylelin1998.com

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
exit - 退出
language - 切换语言
restart - 重启机器人
upgrade - 升级机器人
help - 帮助
```