package code.handler;

import code.config.Config;
import code.config.I18nEnum;
import code.config.I18nLocaleEnum;
import code.config.RequestProxyConfig;
import code.handler.steps.*;
import code.util.*;
import code.util.gpt.GPTRole;
import code.util.gpt.parameter.GPTChatParameter;
import code.util.gpt.parameter.GPTCreateImageParameter;
import code.util.gpt.parameter.GPTMessage;
import code.util.gpt.response.GPTChatResponse;
import code.util.gpt.response.GPTCreateImageResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.io.InputStream;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static code.Main.GlobalConfig;

@Slf4j
public class Handler {

    private static boolean isAdmin(String fromId) {
        return GlobalConfig.getBotAdminId().equals(fromId);
    }

    private static int getVersionInt(String version) {
        String v = version.replaceAll("\\.", "");
        v = v.replaceAll("v", "");
        v = v.replaceAll("version", "");
        return Integer.valueOf(v).intValue();
    }

    private static GPTChatParameter buildGPTChatParameter(String fromId, List<GPTMessage> messages, String content) {
        GPTMessage message = new GPTMessage();
        message.setRole(GPTRole.User.getRole());
        message.setContent(content);
        messages.add(message);

        GPTChatParameter parameter = new GPTChatParameter();
        parameter.setUser(fromId);
        parameter.setStream(true);
        parameter.setModel("gpt-3.5-turbo");
        parameter.setMessages(messages);

        return parameter;
    }

    public static void init() {
        // Chat
        StepsBuilder
                .create()
                .bindCommand(Command.ChatShorter, Command.Chat)
                .debug(GlobalConfig.getDebug())
                .error((Exception e, String chatId, String fromId, Integer replyToMessageId) -> {
                    log.error(ExceptionUtil.getStackTraceWithCustomInfoToStr(e));
                    MessageHandle.sendMessage(chatId, I18nHandle.getText(chatId, I18nEnum.UnknownError), false);
                })
                .steps((String chatId, String fromId, Integer replyToMessageId, String text, int index, List<String> list, Map<String, Object> context) -> {
                    if (StringUtils.isBlank(text)) {
                        MessageHandle.sendMessage(chatId, replyToMessageId,"请发送给我想要问的问题...", false);
                        return StepResult.reject();
                    }

                    String questionText = (text.length() > 15 ? StringUtils.substring(text, 0, 15) : text) + "...";

                    Message message = MessageHandle.sendMessage(chatId, replyToMessageId, String.format("%s\n---\n正在组织语言...", questionText), false);

                    Object messagesObj = context.get("messages");
                    List<GPTMessage> messages = null;
                    if (null != messagesObj) {
                        messages = (List<GPTMessage>) messagesObj;
                    } else {
                        messages = Collections.synchronizedList(new ArrayList<>());
                    }

                    AtomicInteger count = new AtomicInteger();
                    GPTChatParameter gptChatParameter = buildGPTChatParameter(fromId, messages, text);
                    GPTChatResponse response = null;
                    for (int i = 0; i < 3; i++) {
                        response = GPTUtil.chat(RequestProxyConfig.create(), gptChatParameter, (content -> {
                            if (!content.isDone()) {
                                count.incrementAndGet();
                                if (count.get() == 60) {
                                    MessageHandle.editMessage(message, String.format("%s\n---\n%s...", questionText, content.getContent()));
                                    count.set(0);
                                }
                            }
                        }));
                        if (response.isOk()) {
                            break;
                        }
                        try {
                            TimeUnit.SECONDS.sleep((i + 1) * 2);
                        } catch (InterruptedException e) {}
                    }

                    if (!response.isOk()) {
                        MessageHandle.editMessage(message, String.format("糟糕糟糕OMG, 程序居然失灵啦！！！ 错误码: %s, 出现了未知错误， 请重新试试吧！！！", response.getStatusCode()));
                        return StepResult.reject();
                    }

                    StringBuilder builder = new StringBuilder();
                    builder.append(questionText);
                    builder.append("\n");
                    builder.append("---");
                    builder.append("\n");
                    builder.append(response.getContent());
                    builder.append("\n\n");
                    builder.append("---");
                    builder.append("\n");
                    builder.append("你可以继续发送给我想要问的问题， 或者使用 /exit 命令来进行退出此次会话");

                    for (int i = 0; i < 3; i++) {
                        if (MessageHandle.editMessage(message, builder.toString())) {
                            break;
                        }
                        try {
                            TimeUnit.SECONDS.sleep((i + 1) * 2);
                        } catch (InterruptedException e) {}
                    }

                    GPTMessage gptMessage = new GPTMessage();
                    gptMessage.setRole(GPTRole.Assistant.getRole());
                    gptMessage.setContent(response.getContent());

                    messages.add(gptMessage);

                    context.put("messages", messages);

                    return StepResult.reject();
                })
                .build();

        // Image
        StepsBuilder
                .create()
                .bindCommand(Command.Image)
                .debug(GlobalConfig.getDebug())
                .error((Exception e, String chatId, String fromId, Integer replyToMessageId) -> {
                    log.error(ExceptionUtil.getStackTraceWithCustomInfoToStr(e));
                    MessageHandle.sendMessage(chatId, I18nHandle.getText(chatId, I18nEnum.UnknownError), false);
                })
                .steps((String chatId, String fromId, Integer replyToMessageId, String text, int index, List<String> list, Map<String, Object> context) -> {
                    if (StringUtils.isBlank(text)) {
                        MessageHandle.sendMessage(chatId, replyToMessageId, "创建图片失败， 请发送给我想要生成图片的文本解释, 或者输入 /exit 退出", false);
                        return StepResult.reject();
                    }
                    if (text.length() > 1000) {
                        MessageHandle.sendMessage(chatId, replyToMessageId, "创建图片失败， 图片要求文本长度不能大于1000字符, 请重新发送给我想要生成图片的文本解释, 或者输入 /exit 退出", false);
                        return StepResult.reject();
                    }

                    Message message = MessageHandle.sendMessage(chatId, replyToMessageId, "正在尝试生成图片...请稍等...", false);

                    try {
                        GPTCreateImageParameter parameter = new GPTCreateImageParameter();
                        parameter.setUser(fromId);
                        parameter.setPrompt(text);

                        GPTCreateImageResponse image = GPTUtil.createImage(RequestProxyConfig.create(), parameter);
                        if (image.isOk()) {
                            MessageHandle.editMessage(message, "已获取到图片， 正在下载发送...");
                            InputStream inputStream = DownloadUtil.download(RequestProxyConfig.create(), image.getData().get(0).getUrl());
                            MessageHandle.sendImage(chatId, replyToMessageId, "", inputStream);
                        } else {
                            MessageHandle.editMessage(message, String.format("糟糕糟糕OMG, 程序居然失灵啦！！！ 出现了未知错误， 请重新试试吧！！！"));
                        }
                    } finally {
                        MessageHandle.deleteMessage(message);
                    }

                    return StepResult.end();
                })
                .build();

        // Exit
        StepsBuilder
                .create()
                .bindCommand(Command.Exit)
                .debug(GlobalConfig.getDebug())
                .error((Exception e, String chatId, String fromId, Integer replyToMessageId) -> {
                    log.error(ExceptionUtil.getStackTraceWithCustomInfoToStr(e));
                    MessageHandle.sendMessage(chatId, I18nHandle.getText(chatId, I18nEnum.UnknownError), false);
                })
                .steps((String chatId, String fromId, Integer replyToMessageId, String text, int index, List<String> list, Map<String, Object> context) -> {
                    StepsCentre.exit(chatId, fromId);

                    MessageHandle.sendMessage(chatId, replyToMessageId, "退出成功", false);
                    return StepResult.end();
                })
                .build();

        // Language
        StepsBuilder
                .create()
                .bindCommand(Command.Language)
                .debug(GlobalConfig.getDebug())
                .error((Exception e, String chatId, String fromId, Integer replyToMessageId) -> {
                    log.error(ExceptionUtil.getStackTraceWithCustomInfoToStr(e));
                    MessageHandle.sendMessage(chatId, I18nHandle.getText(chatId, I18nEnum.UnknownError), false);
                })
                .init((String chatId, String fromId, Integer replyToMessageId, String text, int index, List<String> list, Map<String, Object> context) -> {
                    ArrayList<InlineKeyboardButton> inlineKeyboardButtons = new ArrayList<>();
                    for (I18nLocaleEnum value : I18nLocaleEnum.values()) {
                        InlineKeyboardButton inlineKeyboardButton = new InlineKeyboardButton();
                        inlineKeyboardButton.setText(value.getDisplayText());
                        inlineKeyboardButton.setCallbackData(StepsCentre.buildCallbackData(chatId, fromId, Command.Language, value.getAlias()));

                        inlineKeyboardButtons.add(inlineKeyboardButton);
                    }

                    MessageHandle.sendInlineKeyboard(chatId, I18nHandle.getText(chatId, I18nEnum.LanguageList), inlineKeyboardButtons);

                    return StepResult.ok();
                })
                .steps((String chatId, String fromId, Integer replyToMessageId, String text, int index, List<String> list, Map<String, Object> context) -> {
                    I18nLocaleEnum alias = I18nLocaleEnum.getI18nLocaleEnumByAlias(text);

                    I18nHandle.save(fromId, alias);

                    MessageHandle.sendMessage(chatId, I18nHandle.getText(fromId, I18nEnum.ChangeLanguageFinish), false);

                    return StepResult.end();
                })
                .build();

        // Restart
        StepsBuilder
                .create()
                .bindCommand(Command.Restart)
                .debug(GlobalConfig.getDebug())
                .error((Exception e, String chatId, String fromId, Integer replyToMessageId) -> {
                    log.error(ExceptionUtil.getStackTraceWithCustomInfoToStr(e));
                    MessageHandle.sendMessage(chatId, I18nHandle.getText(chatId, I18nEnum.UnknownError), false);
                })
                .init((String chatId, String fromId, Integer replyToMessageId, String text, int index, List<String> list, Map<String, Object> context) -> {
                    if (!isAdmin(fromId)) {
                        MessageHandle.sendMessage(chatId, replyToMessageId, "你不是管理员， 无法使用此命令", false);
                        return StepResult.end();
                    }

                    InlineKeyboardButton inlineKeyboardButton = new InlineKeyboardButton();
                    inlineKeyboardButton.setText("确定");
                    inlineKeyboardButton.setCallbackData(StepsCentre.buildCallbackData(chatId, fromId, Command.Restart, "true"));

                    InlineKeyboardButton inlineKeyboardButton2 = new InlineKeyboardButton();
                    inlineKeyboardButton.setText("取消");
                    inlineKeyboardButton.setCallbackData(StepsCentre.buildCallbackData(chatId, fromId, Command.Restart, "false"));

                    MessageHandle.sendInlineKeyboard(chatId, "确定重启机器人吗？", inlineKeyboardButton, inlineKeyboardButton2);

                    return StepResult.ok();
                })
                .steps((String chatId, String fromId, Integer replyToMessageId, String text, int index, List<String> list, Map<String, Object> context) -> {
                    Boolean of = Boolean.valueOf(text);
                    if (of) {
                        MessageHandle.sendMessage(chatId, replyToMessageId, "正在重启...", false);
                        ProgramUtil.restart(Config.MetaData.ProcessName);
                    } else {
                        MessageHandle.sendMessage(chatId, replyToMessageId, "取消成功", false);
                    }
                    return StepResult.end();
                })
                .build();

        // Upgrade
        StepsBuilder
                .create()
                .bindCommand(Command.Upgrade)
                .debug(GlobalConfig.getDebug())
                .error((Exception e, String chatId, String fromId, Integer replyToMessageId) -> {
                    log.error(ExceptionUtil.getStackTraceWithCustomInfoToStr(e));
                    MessageHandle.sendMessage(chatId, I18nHandle.getText(chatId, I18nEnum.UnknownError), false);
                })
                .init((String chatId, String fromId, Integer replyToMessageId, String text, int index, List<String> list, Map<String, Object> context) -> {
                    if (!isAdmin(fromId)) {
                        MessageHandle.sendMessage(chatId, replyToMessageId, "你不是管理员， 无法使用此命令", false);
                        return StepResult.end();
                    }

                    Message message = MessageHandle.sendMessage(chatId, replyToMessageId, "正在获取更新数据...", false);
                    GithubUtil.LatestReleaseResponse release = GithubUtil.getLatestRelease(RequestProxyConfig.create(), Config.MetaData.GitOwner, Config.MetaData.GitRepo);
                    if (release.isOk()) {
                        StringBuilder builder = new StringBuilder();
                        builder.append("确定更新机器人吗? ");
                        builder.append("\n");
                        builder.append("目标版本: ");
                        builder.append(release.getTagName());
                        builder.append("\n");
                        builder.append("目前版本: ");
                        builder.append(Config.MetaData.CurrentVersion);
                        builder.append("\n");
                        builder.append("更新内容: ");
                        builder.append("\n");
                        builder.append(release.getBody());

                        InlineKeyboardButton inlineKeyboardButton = new InlineKeyboardButton();
                        inlineKeyboardButton.setText("确定");
                        inlineKeyboardButton.setCallbackData(StepsCentre.buildCallbackData(chatId, fromId, Command.Upgrade, "true"));

                        InlineKeyboardButton inlineKeyboardButton2 = new InlineKeyboardButton();
                        inlineKeyboardButton.setText("取消");
                        inlineKeyboardButton.setCallbackData(StepsCentre.buildCallbackData(chatId, fromId, Command.Upgrade, "false"));

                        MessageHandle.sendInlineKeyboard(chatId, builder.toString(), inlineKeyboardButton, inlineKeyboardButton2);

                        String url = "";
                        for (GithubUtil.LatestReleaseAsset asset : release.getAssets()) {
                            if (Config.MetaData.JarName.equals(asset.getName())) {
                                url = asset.getBrowserDownloadUrl();
                                break;
                            }
                        }

                        context.put("url", url);

                        return StepResult.ok();
                    } else {
                        MessageHandle.editMessage(message, "获取更新数据失败...");
                        return StepResult.end();
                    }
                })
                .steps((String chatId, String fromId, Integer replyToMessageId, String text, int index, List<String> list, Map<String, Object> context) -> {
                    Boolean of = Boolean.valueOf(text);
                    if (of) {
                        Message message = MessageHandle.sendMessage(chatId, replyToMessageId, "正在更新...", false);
                        String url = (String) context.get("url");

                        AtomicInteger count = new AtomicInteger();
                        boolean b = DownloadUtil.download(
                                RequestProxyConfig.create(),
                                url,
                                "/" + Config.MetaData.ProcessName,
                                (String var1, String var2, Long var3, Long var4) -> {
                                    count.incrementAndGet();
                                    if (count.get() == 10) {
                                        MessageHandle.editMessage(message, String.format("已下载: %s, 总文件大小: %s", BytesUtil.toDisplayStr(var4 - var3), BytesUtil.toDisplayStr(var4)));
                                        count.set(0);
                                    }
                                }
                        );
                        if (b) {
                            ProgramUtil.restart(Config.MetaData.ProcessName);
                        } else {
                            MessageHandle.sendMessage(chatId, replyToMessageId, "更新失败， 重新试试吧", false);
                        }
                    } else {
                        MessageHandle.sendMessage(chatId, replyToMessageId, "取消成功", false);
                    }
                    return StepResult.end();
                })
                .build();
    }

}
