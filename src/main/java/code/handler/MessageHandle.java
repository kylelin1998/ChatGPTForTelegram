package code.handler;

import code.util.ExceptionUtil;
import com.alibaba.fastjson2.JSON;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.telegram.telegrambots.meta.api.methods.ParseMode;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static code.Main.Bot;

@Slf4j
public class MessageHandle {

    public enum MessageError {
        BotWasBlockedByTheUser,

        ;
    }

    @Data
    public static class MessageResponse {
        private boolean ok;
        private Message message;
        private MessageError messageError;
    }

    public static Message sendImage(String chatId, Integer replyToMessageId, String text, InputStream image) {
        SendPhoto sendPhoto = new SendPhoto();
        sendPhoto.setChatId(chatId);
        sendPhoto.setReplyToMessageId(replyToMessageId);
        sendPhoto.setCaption(text);
        sendPhoto.setPhoto(new InputFile(image, "image.png"));

        try {
            return Bot.execute(sendPhoto);
        } catch (TelegramApiException e) {
            log.error(ExceptionUtil.getStackTraceWithCustomInfoToStr(e));
        }
        return null;
    }

    public static Message sendImage(String chatId, Integer replyToMessageId, String text, File image) {
        SendPhoto sendPhoto = new SendPhoto();
        sendPhoto.setChatId(chatId);
        sendPhoto.setReplyToMessageId(replyToMessageId);
        sendPhoto.setCaption(text);
        sendPhoto.setPhoto(new InputFile(image));

        try {
            return Bot.execute(sendPhoto);
        } catch (TelegramApiException e) {
            log.error(ExceptionUtil.getStackTraceWithCustomInfoToStr(e));
        }
        return null;
    }

    public static Message sendInlineKeyboard(String chatId, String text, InlineKeyboardButton... inlineKeyboardButtonList) {
        return sendInlineKeyboard(chatId, text, Arrays.asList(inlineKeyboardButtonList));
    }

    public static Message sendInlineKeyboard(String chatId, String text, List<InlineKeyboardButton> inlineKeyboardButtonList) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(text);

        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

        for (InlineKeyboardButton button : inlineKeyboardButtonList) {
            List<InlineKeyboardButton> list = new ArrayList<>();
            list.add(button);
            keyboard.add(list);
        }

        inlineKeyboardMarkup.setKeyboard(keyboard);
        message.setReplyMarkup(inlineKeyboardMarkup);

        try {
            return Bot.execute(message);
        } catch (TelegramApiException e) {
            log.error(ExceptionUtil.getStackTraceWithCustomInfoToStr(e));
        }
        return null;
    }

    public static Message sendCustomKeyboard(String chatId, String text, KeyboardRow row) {
        List<KeyboardRow> list = new ArrayList<>();
        list.add(row);

        return sendCustomKeyboard(chatId, text, list);
    }

    public static Message sendCustomKeyboard(String chatId, String text, List<KeyboardRow> keyboard) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(text);

        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();

        keyboardMarkup.setKeyboard(keyboard);
        message.setReplyMarkup(keyboardMarkup);

        try {
            return Bot.execute(message);
        } catch (TelegramApiException e) {
            log.error(ExceptionUtil.getStackTraceWithCustomInfoToStr(e));
        }
        return null;
    }

    public static MessageResponse sendMsg(String chatId, String text, boolean webPagePreview) {
        MessageResponse response = new MessageResponse();
        response.setOk(false);

        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.setText(text);
        sendMessage.setParseMode(ParseMode.HTML);
        if (!webPagePreview) {
            sendMessage.disableWebPagePreview();
        }
        try {
            Message execute = Bot.execute(sendMessage);
            response.setOk(true);
            response.setMessage(execute);
            return response;
        } catch (Exception e) {
            String message = e.getMessage();
            if (message.contains("bot was blocked by the user")) {
                response.setMessageError(MessageError.BotWasBlockedByTheUser);
            } else {
                log.error(ExceptionUtil.getStackTraceWithCustomInfoToStr(e, JSON.toJSONString(sendMessage)));
            }
        }

        return response;
    }

    public static Message sendMessage(String chatId, String text, boolean webPagePreview) {
        return sendMessage(chatId, null, text, webPagePreview, true);
    }
    public static Message sendMessage(String chatId, String text, boolean webPagePreview, boolean notification) {
        return sendMessage(chatId, null, text, webPagePreview, notification);
    }
    public static Message sendMessage(String chatId, Integer replyToMessageId, String text, boolean webPagePreview) {
        return sendMessage(chatId, replyToMessageId, text, webPagePreview, true);
    }
    public static Message sendMessage(String chatId, Integer replyToMessageId, String text, boolean webPagePreview, boolean notification) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.setReplyToMessageId(replyToMessageId);
        sendMessage.setText(text);
        sendMessage.setParseMode(ParseMode.HTML);
        if (!notification) {
            sendMessage.disableNotification();
        }
        if (!webPagePreview) {
            sendMessage.disableWebPagePreview();
        }
        return sendMessage(sendMessage);
    }

    public static Message sendMessage(SendMessage sendMessage) {
        try {
            Message execute = Bot.execute(sendMessage);
            return execute;
        } catch (Exception e) {
            log.error(ExceptionUtil.getStackTraceWithCustomInfoToStr(e, JSON.toJSONString(sendMessage)));
        }
        return null;
    }

    public static boolean editMessage(Message message, String text) {
        try {
            EditMessageText editMessageText = new EditMessageText();
            editMessageText.setChatId(message.getChatId());
            editMessageText.setMessageId(message.getMessageId());
            editMessageText.setText(text);

            Bot.execute(editMessageText);
            return true;
        } catch (Exception e) {
            log.error(ExceptionUtil.getStackTraceWithCustomInfoToStr(e, JSON.toJSONString(message)));
        }
        return false;
    }

    public static boolean deleteMessage(Message message) {
        if (null == message) {
            return false;
        }

        DeleteMessage deleteMessage = new DeleteMessage();
        deleteMessage.setChatId(message.getChatId());
        deleteMessage.setMessageId(message.getMessageId());

        try {
            Boolean execute = Bot.execute(deleteMessage);
            return null == execute ? false : execute;
        } catch (Exception e) {
            log.error(ExceptionUtil.getStackTraceWithCustomInfoToStr(e, JSON.toJSONString(deleteMessage)));
        }
        return false;
    }

    public static boolean deleteMessage(DeleteMessage deleteMessage) {
        try {
            Boolean execute = Bot.execute(deleteMessage);
            return null == execute ? false : execute;
        } catch (Exception e) {
            log.error(ExceptionUtil.getStackTraceWithCustomInfoToStr(e, JSON.toJSONString(deleteMessage)));
        }
        return false;
    }

}
