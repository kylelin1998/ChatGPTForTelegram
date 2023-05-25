#!/bin/sh

temp=temp.jar
app=/app/ChatGPTForTelegram-universal.jar
temp_jar=/app/temp.jar

if [ -e $temp ]
then
    echo "updating..."
    rm -rf $app
    mv $temp_jar $app
    echo "updated..."
fi

java -Djava.security.egd=file:/dev/./urandom -DgptToken=$GPT_TOKEN -DbotAdminId=$BOT_ADMIN_ID -DbotName=$BOT_NAME -DbotToken=$BOT_TOKEN -DbotProxy=$PROXY -DbotProxyHost=$PROXY_HOST -DbotProxyPort=$PROXY_PORT -jar $app