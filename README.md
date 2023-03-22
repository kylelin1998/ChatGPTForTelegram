```
docker build -t gptft .
```

```
docker run --name gptft -d -v $(pwd)/logs:/logs -v $(pwd)/ChatGPTForTelegram-universal.jar:/app.jar -v $(pwd)/config:/config  --restart=always gptft
```

```
chat - 发起对话
c - 发起对话
image - 生成图片
language - 切换语言
restart - 重启机器人
upgrade - 升级机器人
help - 帮助
```