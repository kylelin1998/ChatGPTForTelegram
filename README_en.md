### [简体中文](./README.md) | English

![License](https://img.shields.io/badge/license-MIT-green)
[![release](https://img.shields.io/github/v/release/kylelin1998/ChatGPTForTelegram)](https://github.com/kylelin1998/ChatGPTForTelegram/releases/latest)

## Introduction
Youtube： https://youtu.be/a_WLFtRWGzY

哔哩哔哩： https://www.bilibili.com/video/BV1qX4y1z7Dt/

The ChatGPT Telegram Bot based on OpenAI's ChatGPT. It can let you easily to chat or ask.

The bot has some commands that consider different scenes for chatting.

This project is an open-source project that you can trust. Once deployed, the bot's version is easy with a single command to upgrade.

1. Support the voice chat with the bot.
2. Added record & playback mode feature.
3. Support text chat with the bot.
4. Support image generation

The record & playback mode allows you to record chats with the bot, then send 'end_record' to stop and save the recording. Afterward, you can playback the recorded chat. It can help your work and is a nice feature.

## Recent Update Log
Open-source ChatGPT TG Robot v1.0.50 Update Notes Video
- ⭐ Youtube: https://youtu.be/9hczaDzOvGA
- ⭐ Bilibili: https://www.bilibili.com/video/BV1wV41137Yf/

1. Added support for multiple ChatGPT API keys, which switch randomly. Invalid keys are automatically notified to the administrator.
2. Added support for streamlined replies. When enabled, certain commands will remove the display of the question and exit prompts, only showing the reply content.
3. Enhanced onboarding process. The recording and playback of conversations now support variable substitution. Simply send the text and no longer need to add additional text for guidance.

## Deploy
The bot's deploy steps based on the Docker, its upgrade feature also based on the Docker, so please use the Docker to deploy it in case appear error.

### Deployment method 1 (recommended)
⭐ Youtube: https://youtu.be/mNg6TFyozZk

⭐ 哔哩哔哩： https://www.bilibili.com/video/BV1qF411f7pg/

#### One-click deployment
```
docker run --name gpttb -d -v $(pwd)/config:/app/config -e GPT_TOKEN=YourGPTApiKey -e BOT_ADMIN_ID=AdminChatId -e BOT_NAME=BotUsername -e BOT_TOKEN=BotToken --restart=always kylelin1998/chatgpt-tg-bot
```
#### One-click deployment (with proxy enabled)
```
docker run --name gpttb -d -v $(pwd)/config:/app/config -e GPT_TOKEN=YourGPTApiKey -e BOT_ADMIN_ID=AdminChatId -e BOT_NAME=BotUsername -e BOT_TOKEN=BotToken -e PROXY=true -e PROXY_HOST=127.0.0.1 -e PROXY_PORT=7890 --restart=always kylelin1998/chatgpt-tg-bot
```

### Deployment method 2 (not recommended)
Youtube：https://youtu.be/CiDxb1ESijQ

哔哩哔哩： https://www.bilibili.com/video/BV1Ts4y1S7bn/

### Prepare
![ff3379f00b462db7b016f361c9b8fb7cd9097dc8.png](https://openimg.kylelin1998.com/img/ff3379f00b462db7b016f361c9b8fb7cd9097dc8.png)

To start, create a folder named whatever you prefer on your server. 
Then create another folder named config and the config folder must contains a json file named config.json in, then transfer ChatGPTForTelegram-universal.jar, run.sh and Dockerfile to the folder.
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
Open status: Anyone can use this bot.
Close status: Only permission chat id list can use this bot. 
on proxy -> Whether to open proxy
bot admin id -> Bot's admin, the id is chat id of Telegram.
bot name, and token you -> @BotFather has given bot name,  bot token
permission chat id array -> Allow using the bot.
block_chat_id_array -> Not allow using the bot.
```

### First step:
Build a docker image for use.
```
docker build -t gptft .
```

### Second step:
Run the docker image of just then build.
```
docker run --name gptft -d -v $(pwd):/app --restart=always gptft
```

## Usage
How to use the bot's commands:
```
chat - The current mode is continuous chat mode
c - The current mode is continuous chat mode
ask - Ask a problem
a - Ask a problem
cml - The current mode is chat message limit mode
nccm - The current mode is none of message context mode
image - Create a image for you
record - Record chat messages.
p - Playback chat messages.
record_list - Manage the record list.
exit - Exit chat
language - Change language
admin - Admin
restart - Restart the bot
upgrade - Upgrade the bot
help - Help
```

![560c3fe7450239da5ad0d9638cfd4fd66551d576.png](https://openimg.kylelin1998.com/img/560c3fe7450239da5ad0d9638cfd4fd66551d576.png)
![9d57081a0e248f157e427618c3430a44f3b1785d.png](https://openimg.kylelin1998.com/img/9d57081a0e248f157e427618c3430a44f3b1785d.png)
