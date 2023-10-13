package code.handler.commands;

import code.config.*;
import code.handler.Command;
import code.handler.I18nHandle;
import code.handler.StepsCenter;
import code.handler.message.InlineKeyboardButtonBuilder;
import code.handler.message.InlineKeyboardButtonListBuilder;
import code.handler.message.MessageHandle;
import code.handler.steps.StepResult;
import code.handler.steps.StepsBuilder;
import code.handler.steps.StepsChatSession;
import code.handler.store.ChatButtonsStore;
import code.handler.store.GptTokenStore;
import code.util.*;
import code.util.ffmpeg.FfmpegDownloadUrl;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static code.Main.GlobalConfig;

@Slf4j
public class AdminCommands {

    private static boolean isAdmin(String fromId) {
        return GlobalConfig.getBotAdminId().equals(fromId);
    }

    public static void init() {
        // Admin
        StepsBuilder
                .create()
                .bindCommand(Command.Admin)
                .debug(GlobalConfig.getDebug())
                .error((Exception e, StepsChatSession session) -> {
                    log.error(ExceptionUtil.getStackTraceWithCustomInfoToStr(e));
                    MessageHandle.sendMessage(session.getChatId(), I18nHandle.getText(session.getFromId(), I18nEnum.UnknownError), false);
                })
                .steps((StepsChatSession session, int index, List<String> list, Map<String, Object> context) -> {
                    if (!isAdmin(session.getFromId())) {
                        MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), I18nHandle.getText(session.getFromId(), I18nEnum.YouAreNotAnAdmin), false);
                        return StepResult.end();
                    }

                    List<List<InlineKeyboardButton>> keyboardButton = InlineKeyboardButtonListBuilder
                            .create()
                            .add(InlineKeyboardButtonBuilder
                                    .create()
                                    .add(I18nHandle.getText(session.getFromId(), I18nEnum.SetChatButtons), StepsCenter.buildCallbackData(true, session, Command.SetChatButtons, null))
                                    .build()
                            )
                            .add(InlineKeyboardButtonBuilder
                                    .create()
                                    .add(I18nHandle.getText(session.getFromId(), I18nEnum.SetVoiceStatus), StepsCenter.buildCallbackData(true, session, Command.SetVoiceStatus, null))
                                    .add(I18nHandle.getText(session.getFromId(), I18nEnum.ChangeModel), StepsCenter.buildCallbackData(true, session, Command.ChangeModel, null))
                                    .build()
                            )
                            .add(InlineKeyboardButtonBuilder
                                    .create()
                                    .add(I18nHandle.getText(session.getFromId(), I18nEnum.SetOpenStatus), StepsCenter.buildCallbackData(true, session, Command.SetOpenStatus, null))
                                    .add(I18nHandle.getText(session.getFromId(), I18nEnum.UpdateConfig), StepsCenter.buildCallbackData(true, session, Command.UpdateConfig, null))
                                    .build()
                            )
                            .add(InlineKeyboardButtonBuilder
                                    .create()
                                    .add(I18nHandle.getText(session.getFromId(), I18nEnum.SetGptToken), StepsCenter.buildCallbackData(true, session, Command.SetGptToken, null))
                                    .build()
                            )
                            .add(InlineKeyboardButtonBuilder
                                    .create()
                                    .add(I18nHandle.getText(session.getFromId(), I18nEnum.SetConciseReplies), StepsCenter.buildCallbackData(true, session, Command.SetConciseReplies, null))
                                    .build()
                            )
                            .add(InlineKeyboardButtonBuilder
                                    .create()
                                    .add(I18nHandle.getText(session.getFromId(), I18nEnum.SetStartText), StepsCenter.buildCallbackData(true, session, Command.SetStartText, null))
                                    .build()
                            )
                            .add(InlineKeyboardButtonBuilder
                                    .create()
                                    .add(I18nHandle.getText(session.getFromId(), I18nEnum.Restart), StepsCenter.buildCallbackData(true, session, Command.Restart, null))
                                    .add(I18nHandle.getText(session.getFromId(), I18nEnum.Upgrade), StepsCenter.buildCallbackData(true, session, Command.Upgrade, null))
                                    .build()
                            )
                            .build();

                    ConfigSettings config = Config.readConfig();

                    StringBuilder builder = new StringBuilder();
                    builder.append(I18nHandle.getText(session.getFromId(), I18nEnum.SetVoiceStatus) + ": ");
                    builder.append(config.getVoice() ? I18nHandle.getText(session.getFromId(), I18nEnum.Open) : I18nHandle.getText(session.getFromId(), I18nEnum.Close));
                    builder.append("\n");
                    builder.append(I18nHandle.getText(session.getFromId(), I18nEnum.SetOpenStatus) + ": ");
                    builder.append(config.getOpen() ? I18nHandle.getText(session.getFromId(), I18nEnum.Open) : I18nHandle.getText(session.getFromId(), I18nEnum.Close));
                    builder.append("\n");
                    builder.append(I18nHandle.getText(session.getFromId(), I18nEnum.SetConciseReplies) + ": ");
                    builder.append(config.getConciseReplies() ? I18nHandle.getText(session.getFromId(), I18nEnum.Open) : I18nHandle.getText(session.getFromId(), I18nEnum.Close));
                    builder.append("\n");
                    builder.append(I18nHandle.getText(session.getFromId(), I18nEnum.Model) + ": ");
                    builder.append(config.getGptModel());
                    builder.append("\n\n");
                    Properties properties = System.getProperties();
                    builder.append("---");
                    builder.append("\n");
                    builder.append("os.name: ");
                    builder.append(properties.getProperty("os.name"));
                    builder.append("\n");
                    builder.append("os.arch: ");
                    builder.append(properties.getProperty("os.arch"));

                    MessageHandle.sendInlineKeyboardList(session.getChatId(), builder.toString(),  keyboardButton);

                    return StepResult.end();
                })
                .build();

        // Set Concise Replies
        StepsBuilder
                .create()
                .bindCommand(Command.SetConciseReplies)
                .debug(GlobalConfig.getDebug())
                .error((Exception e, StepsChatSession session) -> {
                    log.error(ExceptionUtil.getStackTraceWithCustomInfoToStr(e));
                    MessageHandle.sendMessage(session.getChatId(), I18nHandle.getText(session.getFromId(), I18nEnum.UnknownError), false);
                })
                .init((StepsChatSession session, int index, List<String> list, Map<String, Object> context) -> {
                    if (!isAdmin(session.getFromId())) {
                        MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), I18nHandle.getText(session.getFromId(), I18nEnum.YouAreNotAnAdmin), false);
                        return StepResult.end();
                    }

                    List<InlineKeyboardButton> buttons = InlineKeyboardButtonBuilder
                            .create()
                            .add(I18nHandle.getText(session.getFromId(), I18nEnum.Open), StepsCenter.buildCallbackData(false, session, Command.SetConciseReplies, "open"))
                            .add(I18nHandle.getText(session.getFromId(), I18nEnum.Close), StepsCenter.buildCallbackData(false, session, Command.SetConciseReplies, "close"))
                            .add(I18nHandle.getText(session.getFromId(), I18nEnum.Cancel), StepsCenter.buildCallbackData(false, session, Command.SetConciseReplies, "cancel"))
                            .build();

                    MessageHandle.sendInlineKeyboard(session.getChatId(), I18nHandle.getText(session.getFromId(), I18nEnum.PleaseChooseConciseReplies),  buttons);
                    return StepResult.ok();
                })
                .steps((StepsChatSession session, int index, List<String> list, Map<String, Object> context) -> {
                    String text = session.getText();
                    if (text.equals("open") || text.equals("close")) {
                        ConfigSettings config = Config.readConfig();
                        config.setConciseReplies(text.equals("open"));
                        boolean b = Config.saveConfig(config);
                        if (b) {
                            MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), I18nHandle.getText(session.getFromId(), I18nEnum.UpdateSucceeded), false);
                        } else {
                            MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), I18nHandle.getText(session.getFromId(), I18nEnum.UpdateFailed), false);
                        }

                        return StepResult.end();
                    } else {
                        MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), I18nHandle.getText(session.getFromId(), I18nEnum.CancelSucceeded), false);
                        return StepResult.end();
                    }
                })
                .build();

        // Set Gpt Token
        StepsBuilder
                .create()
                .bindCommand(Command.SetGptToken)
                .debug(GlobalConfig.getDebug())
                .error((Exception e, StepsChatSession session) -> {
                    log.error(ExceptionUtil.getStackTraceWithCustomInfoToStr(e));
                    MessageHandle.sendMessage(session.getChatId(), I18nHandle.getText(session.getFromId(), I18nEnum.UnknownError), false);
                })
                .init((StepsChatSession session, int index, List<String> list, Map<String, Object> context) -> {
                    if (!isAdmin(session.getFromId())) {
                        MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), I18nHandle.getText(session.getFromId(), I18nEnum.YouAreNotAnAdmin), false);
                        return StepResult.end();
                    }

                    MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), GptTokenStore.getListText(session.getFromId()), false);

                    MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), I18nHandle.getText(session.getFromId(), I18nEnum.PleaseSendMeGptToken), false);
                    return StepResult.ok();
                })
                .steps((StepsChatSession session, int index, List<String> list, Map<String, Object> context) -> {
                    String text = session.getText();
                    if (StringUtils.isBlank(text)) {
                        MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), I18nHandle.getText(session.getFromId(), I18nEnum.FormatError), false);
                        return StepResult.reject();
                    }
                    if (text.equals("-1")) {
                        GptTokenStore.deleteAll();
                    } else {
                        String[] split = StringUtils.split(text, "\n");
                        ArrayList<String> tokens = new ArrayList<>();
                        for (String s : split) {
                            if (StringUtils.startsWith(s, "sk-")) {
                                tokens.add(s);
                            }
                        }
                        if (tokens.isEmpty()) {
                            MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), I18nHandle.getText(session.getFromId(), I18nEnum.FormatError), false);
                            return StepResult.reject();
                        }
                        GptTokenStore.forceSave(tokens);
                    }

                    MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), I18nHandle.getText(session.getFromId(), I18nEnum.UpdateSucceeded), false);
                    MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), GptTokenStore.getListText(session.getFromId()), false);

                    return StepResult.ok();
                })
                .build();

        // Set Chat Buttons
        StepsBuilder
                .create()
                .bindCommand(Command.SetChatButtons)
                .debug(GlobalConfig.getDebug())
                .error((Exception e, StepsChatSession session) -> {
                    log.error(ExceptionUtil.getStackTraceWithCustomInfoToStr(e));
                    MessageHandle.sendMessage(session.getChatId(), I18nHandle.getText(session.getFromId(), I18nEnum.UnknownError), false);
                })
                .init((StepsChatSession session, int index, List<String> list, Map<String, Object> context) -> {
                    if (!isAdmin(session.getFromId())) {
                        MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), I18nHandle.getText(session.getFromId(), I18nEnum.YouAreNotAnAdmin), false);
                        return StepResult.end();
                    }

                    ConfigSettings config = Config.readConfig();
                    String chatButtons = config.getChatButtons();
                    if (StringUtils.isNotBlank(chatButtons)) {
                        MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), chatButtons, false);
                    }

                    MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), I18nHandle.getText(session.getFromId(), I18nEnum.PleaseSendMeChatButtons), false);
                    return StepResult.ok();
                })
                .steps((StepsChatSession session, int index, List<String> list, Map<String, Object> context) -> {
                    String text = session.getText();
                    if (StringUtils.isBlank(text)) {
                        MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), I18nHandle.getText(session.getFromId(), I18nEnum.FormatError), false);
                        return StepResult.reject();
                    }
                    if (!text.equals("-1")) {
                        Optional<ChatButtonsStore.ChatButtonsToInlineKeyboardButtons> buttons = ChatButtonsStore.verify(text);
                        if (!buttons.isPresent()) {
                            MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), I18nHandle.getText(session.getFromId(), I18nEnum.FormatError), false);
                            return StepResult.reject();
                        }
                        ChatButtonsStore.ChatButtonsToInlineKeyboardButtons keyboardButtons = buttons.get();

                        for (Map.Entry<String, List<InlineKeyboardButton>> entry : keyboardButtons.getMap().entrySet()) {
                            List<List<InlineKeyboardButton>> build = InlineKeyboardButtonListBuilder
                                    .create()
                                    .add(entry.getValue())
                                    .build();
                            MessageHandle.sendInlineKeyboardList(session.getChatId(), session.getReplyToMessageId(), entry.getKey(), build);
                        }
                    }
                    ChatButtonsStore.set(text);

                    ConfigSettings config = Config.readConfig();
                    config.setChatButtons(text);
                    Config.saveConfig(config);
                    MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), I18nHandle.getText(session.getFromId(), I18nEnum.UpdateSucceeded), false);
                    return StepResult.ok();
                })
                .build();

        // Set voice status
        StepsBuilder
                .create()
                .bindCommand(Command.SetVoiceStatus)
                .debug(GlobalConfig.getDebug())
                .error((Exception e, StepsChatSession session) -> {
                    log.error(ExceptionUtil.getStackTraceWithCustomInfoToStr(e));
                    MessageHandle.sendMessage(session.getChatId(), I18nHandle.getText(session.getFromId(), I18nEnum.UnknownError), false);
                })
                .init((StepsChatSession session, int index, List<String> list, Map<String, Object> context) -> {
                    if (!isAdmin(session.getFromId())) {
                        MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), I18nHandle.getText(session.getFromId(), I18nEnum.YouAreNotAnAdmin), false);
                        return StepResult.end();
                    }

                    List<InlineKeyboardButton> buttons = InlineKeyboardButtonBuilder
                            .create()
                            .add(I18nHandle.getText(session.getFromId(), I18nEnum.Open), StepsCenter.buildCallbackData(false, session, Command.SetVoiceStatus, "open"))
                            .add(I18nHandle.getText(session.getFromId(), I18nEnum.Close), StepsCenter.buildCallbackData(false, session, Command.SetVoiceStatus, "close"))
                            .add(I18nHandle.getText(session.getFromId(), I18nEnum.Cancel), StepsCenter.buildCallbackData(false, session, Command.SetVoiceStatus, "cancel"))
                            .build();

                    MessageHandle.sendInlineKeyboard(session.getChatId(), I18nHandle.getText(session.getFromId(), I18nEnum.ChooseVoiceStatus),  buttons);
                    return StepResult.ok();
                })
                .steps((StepsChatSession session, int index, List<String> list, Map<String, Object> context) -> {
                    String text = session.getText();
                    if (text.equals("open") || text.equals("close")) {
                        if (text.equals("open")) {
                            java.io.File file = new java.io.File(Config.FFMPEGDir);
                            java.io.File file2 = new java.io.File(Config.FFMPEGPath);
                            if (!file.exists() || !file2.exists()) {
                                if (file.exists()) {
                                    file.delete();
                                }
                                Message message = MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), I18nHandle.getText(session.getFromId(), I18nEnum.DownloadingXXX, "FFMPEG"), false);

                                FfmpegDownloadUrl ffmpegDownloadUrl = FfmpegDownloadUrl.getFfmpegDownloadUrl();

                                AtomicInteger count = new AtomicInteger();
                                String originName = ffmpegDownloadUrl.getUrl().substring(ffmpegDownloadUrl.getUrl().lastIndexOf("/") + 1);
                                String temp = Config.CurrentDir + java.io.File.separator + originName;
                                log.info("temp: " + temp);
                                boolean b = DownloadUtil.download(
                                        RequestProxyConfig.create(),
                                        ffmpegDownloadUrl.getUrl(),
                                        temp,
                                        (String var1, String var2, Long var3, Long var4) -> {
                                            if ((var4 - var3) > 0) {
                                                count.incrementAndGet();
                                                if (count.get() == 1000) {
                                                    MessageHandle.editMessage(message, I18nHandle.getText(session.getFromId(), I18nEnum.Downloaded, BytesUtil.toDisplayStr(var3), BytesUtil.toDisplayStr(var4)));
                                                    count.set(0);
                                                }
                                            }
                                        }
                                );

                                if (b) {
                                    MessageHandle.editMessage(message, I18nHandle.getText(session.getFromId(), I18nEnum.Unzipping));
                                    switch (ffmpegDownloadUrl) {
                                        case Windows:
                                            try {
                                                CompressUtil.unzip(temp, new java.io.File(Config.CurrentDir));
                                                new java.io.File(Config.CurrentDir + java.io.File.separator + StringUtils.removeEnd(originName, ".zip")).renameTo(new java.io.File(Config.FFMPEGDir));
                                            } catch (IOException e) {
                                                log.error(ExceptionUtil.getStackTraceWithCustomInfoToStr(e));
                                                MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), I18nHandle.getText(session.getFromId(), I18nEnum.UnknownError), false);
                                                return StepResult.end();
                                            } finally {
                                                new java.io.File(temp).delete();
                                            }
                                            break;
                                        default:
                                            try {
                                                CompressUtil.tarXZUnArchiver(temp, Config.CurrentDir);
                                                java.io.File currentFile = new java.io.File(Config.CurrentDir);
                                                for (java.io.File listFile : currentFile.listFiles()) {
                                                    String name = listFile.getName();
                                                    if (name.contains("ffmpeg") && name.contains("static") && !name.contains("tar.xz")) {
                                                        listFile.renameTo(new java.io.File(Config.FFMPEGDir));
                                                        break;
                                                    }
                                                }

                                            } finally {
                                                new java.io.File(temp).delete();
                                            }
                                            break;
                                    }
                                } else {
                                    MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), I18nHandle.getText(session.getFromId(), I18nEnum.UnknownError), false);
                                    return StepResult.end();
                                }
                            }

                        }

                        ConfigSettings config = Config.readConfig();
                        config.setVoice(text.equals("open"));
                        boolean b = Config.saveConfig(config);
                        if (b) {
                            MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), I18nHandle.getText(session.getFromId(), I18nEnum.UpdateSucceeded), false);
                        } else {
                            MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), I18nHandle.getText(session.getFromId(), I18nEnum.UpdateFailed), false);
                        }

                        return StepResult.end();
                    } else {
                        MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), I18nHandle.getText(session.getFromId(), I18nEnum.CancelSucceeded), false);
                        return StepResult.end();
                    }
                })
                .build();

        // Set open status
        StepsBuilder
                .create()
                .bindCommand(Command.SetOpenStatus)
                .debug(GlobalConfig.getDebug())
                .error((Exception e, StepsChatSession session) -> {
                    log.error(ExceptionUtil.getStackTraceWithCustomInfoToStr(e));
                    MessageHandle.sendMessage(session.getChatId(), I18nHandle.getText(session.getFromId(), I18nEnum.UnknownError), false);
                })
                .init((StepsChatSession session, int index, List<String> list, Map<String, Object> context) -> {
                    if (!isAdmin(session.getFromId())) {
                        MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), I18nHandle.getText(session.getFromId(), I18nEnum.YouAreNotAnAdmin), false);
                        return StepResult.end();
                    }

                    List<InlineKeyboardButton> buttons = InlineKeyboardButtonBuilder
                            .create()
                            .add(I18nHandle.getText(session.getFromId(), I18nEnum.Open), StepsCenter.buildCallbackData(false, session, Command.SetOpenStatus, "open"))
                            .add(I18nHandle.getText(session.getFromId(), I18nEnum.Close), StepsCenter.buildCallbackData(false, session, Command.SetOpenStatus, "close"))
                            .add(I18nHandle.getText(session.getFromId(), I18nEnum.Cancel), StepsCenter.buildCallbackData(false, session, Command.SetOpenStatus, "cancel"))
                            .build();

                    MessageHandle.sendInlineKeyboard(session.getChatId(), I18nHandle.getText(session.getFromId(), I18nEnum.ChooseOpenStatus),  buttons);
                    return StepResult.ok();
                })
                .steps((StepsChatSession session, int index, List<String> list, Map<String, Object> context) -> {
                    String text = session.getText();
                    if (text.equals("open") || text.equals("close")) {
                        ConfigSettings config = Config.readConfig();
                        config.setOpen(text.equals("open"));
                        boolean b = Config.saveConfig(config);
                        if (b) {
                            MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), I18nHandle.getText(session.getFromId(), I18nEnum.UpdateSucceeded), false);
                        } else {
                            MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), I18nHandle.getText(session.getFromId(), I18nEnum.UpdateFailed), false);
                        }

                        return StepResult.end();
                    } else {
                        MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), I18nHandle.getText(session.getFromId(), I18nEnum.CancelSucceeded), false);
                        return StepResult.end();
                    }
                })
                .build();

        // Change model
        StepsBuilder
                .create()
                .bindCommand(Command.ChangeModel)
                .debug(GlobalConfig.getDebug())
                .error((Exception e, StepsChatSession session) -> {
                    log.error(ExceptionUtil.getStackTraceWithCustomInfoToStr(e));
                    MessageHandle.sendMessage(session.getChatId(), I18nHandle.getText(session.getFromId(), I18nEnum.UnknownError), false);
                })
                .init((StepsChatSession session, int index, List<String> list, Map<String, Object> context) -> {
                    if (!isAdmin(session.getFromId())) {
                        MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), I18nHandle.getText(session.getFromId(), I18nEnum.YouAreNotAnAdmin), false);
                        return StepResult.end();
                    }

                    MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), I18nHandle.getText(session.getFromId(), I18nEnum.PleaseSendMeTheModelYouWantToChange), false);
                    MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), "gpt-4, gpt-4-0314, gpt-4-32k, gpt-4-32k-0314, gpt-3.5-turbo, gpt-3.5-turbo-0301", false);
                    return StepResult.ok();
                })
                .steps((StepsChatSession session, int index, List<String> list, Map<String, Object> context) -> {
                    String text = session.getText();

                    ConfigSettings config = Config.readConfig();
                    config.setGptModel(text);
                    boolean b = Config.saveConfig(config);
                    if (b) {
                        MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), I18nHandle.getText(session.getFromId(), I18nEnum.UpdateSucceeded), false);
                    } else {
                        MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), I18nHandle.getText(session.getFromId(), I18nEnum.UpdateFailed), false);
                    }
                    return StepResult.ok();
                })
                .build();

        StepsBuilder
                .create()
                .bindCommand(Command.SetStartText)
                .debug(GlobalConfig.getDebug())
                .error((Exception e, StepsChatSession session) -> {
                    log.error(ExceptionUtil.getStackTraceWithCustomInfoToStr(e));
                    MessageHandle.sendMessage(session.getChatId(), I18nHandle.getText(session.getFromId(), I18nEnum.UnknownError), false);
                })
                .init((StepsChatSession session, int index, List<String> list, Map<String, Object> context) -> {
                    if (!isAdmin(session.getFromId())) {
                        MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), I18nHandle.getText(session.getFromId(), I18nEnum.YouAreNotAnAdmin), false);
                        return StepResult.end();
                    }

                    MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), I18nHandle.getText(session.getFromId(), I18nEnum.PleaseSendMeStartText), false);
                    return StepResult.ok();
                })
                .steps((StepsChatSession session, int index, List<String> list, Map<String, Object> context) -> {
                    String text = session.getText();

                    ConfigSettings config = Config.readConfig();
                    config.setStartText(text);
                    boolean b = Config.saveConfig(config);
                    if (b) {
                        MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), I18nHandle.getText(session.getFromId(), I18nEnum.UpdateSucceeded), false);
                    } else {
                        MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), I18nHandle.getText(session.getFromId(), I18nEnum.UpdateFailed), false);
                    }
                    return StepResult.ok();
                })
                .build();

        // Update config
        StepsBuilder
                .create()
                .bindCommand(Command.UpdateConfig)
                .debug(GlobalConfig.getDebug())
                .error((Exception e, StepsChatSession session) -> {
                    log.error(ExceptionUtil.getStackTraceWithCustomInfoToStr(e));
                    MessageHandle.sendMessage(session.getChatId(), I18nHandle.getText(session.getFromId(), I18nEnum.UnknownError), false);
                })
                .init((StepsChatSession session, int index, List<String> list, Map<String, Object> context) -> {
                    if (!isAdmin(session.getFromId())) {
                        MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), I18nHandle.getText(session.getFromId(), I18nEnum.YouAreNotAnAdmin), false);
                        return StepResult.end();
                    }

                    List<InlineKeyboardButton> buttons = InlineKeyboardButtonBuilder
                            .create()
                            .add(I18nHandle.getText(session.getFromId(), I18nEnum.Confirm), StepsCenter.buildCallbackData(false, session, Command.UpdateConfig, "confirm"))
                            .add(I18nHandle.getText(session.getFromId(), I18nEnum.Cancel), StepsCenter.buildCallbackData(false, session, Command.UpdateConfig, "cancel"))
                            .build();
                    ConfigSettings config = Config.readConfig();

                    MessageHandle.sendMessage(session.getFromId(), JSON.toJSONString(config, JSONWriter.Feature.PrettyFormat), false);
                    MessageHandle.sendInlineKeyboard(session.getChatId(), I18nHandle.getText(session.getFromId(), I18nEnum.AreYouSureToUpdateTheConfig),  buttons);
                    return StepResult.ok();
                })
                .steps((StepsChatSession session, int index, List<String> list, Map<String, Object> context) -> {
                    String text = session.getText();
                    if (text.equals("confirm")) {
                        MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), I18nHandle.getText(session.getFromId(), I18nEnum.PleaseSendMeConfigContent), false);
                        return StepResult.ok();
                    } else {
                        MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), I18nHandle.getText(session.getFromId(), I18nEnum.CancelSucceeded), false);
                        return StepResult.end();
                    }
                }, (StepsChatSession session, int index, List<String> list, Map<String, Object> context) -> {
                    String text = session.getText();
                    ConfigSettings configSettings = Config.verifyConfig(text);
                    if (null == configSettings) {
                        MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), I18nHandle.getText(session.getFromId(), I18nEnum.UpdateConfigFail), false);
                        return StepResult.reject();
                    }

                    boolean b = Config.saveConfig(configSettings);
                    if (b) {
                        MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), I18nHandle.getText(session.getFromId(), I18nEnum.UpdateSucceeded), false);
                    } else {
                        MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), I18nHandle.getText(session.getFromId(), I18nEnum.UpdateFailed), false);
                    }

                    return StepResult.end();
                })
                .build();

        // Language
        StepsBuilder
                .create()
                .bindCommand(Command.Language)
                .debug(GlobalConfig.getDebug())
                .error((Exception e, StepsChatSession session) -> {
                    log.error(ExceptionUtil.getStackTraceWithCustomInfoToStr(e));
                    MessageHandle.sendMessage(session.getChatId(), I18nHandle.getText(session.getFromId(), I18nEnum.UnknownError), false);
                })
                .init((StepsChatSession session, int index, List<String> list, Map<String, Object> context) -> {
                    ArrayList<InlineKeyboardButton> inlineKeyboardButtons = new ArrayList<>();
                    for (I18nLocaleEnum value : I18nLocaleEnum.values()) {
                        InlineKeyboardButton inlineKeyboardButton = new InlineKeyboardButton();
                        inlineKeyboardButton.setText(value.getDisplayText());
                        inlineKeyboardButton.setCallbackData(StepsCenter.buildCallbackData(false, session, Command.Language, value.getAlias()));

                        inlineKeyboardButtons.add(inlineKeyboardButton);
                    }

                    MessageHandle.sendInlineKeyboard(session.getChatId(), I18nHandle.getText(session.getFromId(), I18nEnum.LanguageList), inlineKeyboardButtons);

                    return StepResult.ok();
                })
                .steps((StepsChatSession session, int index, List<String> list, Map<String, Object> context) -> {
                    I18nLocaleEnum alias = I18nLocaleEnum.getI18nLocaleEnumByAlias(session.getText());

                    I18nHandle.save(session.getFromId(), alias);

                    MessageHandle.sendMessage(session.getChatId(), I18nHandle.getText(session.getFromId(), I18nEnum.ChangeLanguageFinish), false);

                    return StepResult.end();
                })
                .build();

        // Restart
        StepsBuilder
                .create()
                .bindCommand(Command.Restart)
                .debug(GlobalConfig.getDebug())
                .error((Exception e, StepsChatSession session) -> {
                    log.error(ExceptionUtil.getStackTraceWithCustomInfoToStr(e));
                    MessageHandle.sendMessage(session.getChatId(), I18nHandle.getText(session.getFromId(), I18nEnum.UnknownError), false);
                })
                .init((StepsChatSession session, int index, List<String> list, Map<String, Object> context) -> {
                    if (!isAdmin(session.getFromId())) {
                        MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), I18nHandle.getText(session.getFromId(), I18nEnum.YouAreNotAnAdmin), false);
                        return StepResult.end();
                    }

                    InlineKeyboardButton inlineKeyboardButton = new InlineKeyboardButton();
                    inlineKeyboardButton.setText(I18nHandle.getText(session.getFromId(), I18nEnum.Confirm));
                    inlineKeyboardButton.setCallbackData(StepsCenter.buildCallbackData(false, session, Command.Restart, "true"));

                    InlineKeyboardButton inlineKeyboardButton2 = new InlineKeyboardButton();
                    inlineKeyboardButton2.setText(I18nHandle.getText(session.getFromId(), I18nEnum.Cancel));
                    inlineKeyboardButton2.setCallbackData(StepsCenter.buildCallbackData(false, session, Command.Restart, "false"));

                    MessageHandle.sendInlineKeyboard(session.getChatId(), I18nHandle.getText(session.getFromId(), I18nEnum.AreYouSureToRestartRightNow), inlineKeyboardButton, inlineKeyboardButton2);

                    return StepResult.ok();
                })
                .steps((StepsChatSession session, int index, List<String> list, Map<String, Object> context) -> {
                    Boolean of = Boolean.valueOf(session.getText());
                    if (of) {
                        MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), I18nHandle.getText(session.getFromId(), I18nEnum.Restarting), false);
                        ProgramUtil.restart(Config.MetaData.ProcessName);
                    } else {
                        MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), I18nHandle.getText(session.getFromId(), I18nEnum.CancelSucceeded), false);
                    }
                    return StepResult.end();
                })
                .build();

        // Upgrade
        StepsBuilder
                .create()
                .bindCommand(Command.Upgrade)
                .debug(GlobalConfig.getDebug())
                .error((Exception e, StepsChatSession stepsChatSession) -> {
                    log.error(ExceptionUtil.getStackTraceWithCustomInfoToStr(e));
                    MessageHandle.sendMessage(stepsChatSession.getChatId(), I18nHandle.getText(stepsChatSession.getFromId(), I18nEnum.UnknownError), false);
                })
                .init((StepsChatSession session, int index, List<String> list, Map<String, Object> context) -> {
                    if (!isAdmin(session.getFromId())) {
                        MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), I18nHandle.getText(session.getFromId(), I18nEnum.YouAreNotAnAdmin), false);
                        return StepResult.end();
                    }

                    Message message = MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), I18nHandle.getText(session.getFromId(), I18nEnum.GettingUpdateData), false);
                    GithubUtil.LatestReleaseResponse release = GithubUtil.getLatestRelease(RequestProxyConfig.create(), Config.MetaData.GitOwner, Config.MetaData.GitRepo);
                    if (release.isOk()) {
                        StringBuilder builder = new StringBuilder();
                        builder.append(I18nHandle.getText(session.getFromId(), I18nEnum.AreYouSureToUpgradeThisBotRightNow));
                        builder.append("\n");
                        builder.append(I18nHandle.getText(session.getFromId(), I18nEnum.TargetVersion) + ": ");
                        builder.append(release.getTagName());
                        builder.append("\n");
                        builder.append(I18nHandle.getText(session.getFromId(), I18nEnum.CurrentVersion) + ": ");
                        builder.append(Config.MetaData.CurrentVersion);
                        builder.append("\n");
                        builder.append(I18nHandle.getText(session.getFromId(), I18nEnum.UpdateLogs) + ": ");
                        builder.append("\n");
                        builder.append(release.getBody());

                        InlineKeyboardButton inlineKeyboardButton = new InlineKeyboardButton();
                        inlineKeyboardButton.setText(I18nHandle.getText(session.getFromId(), I18nEnum.Confirm));
                        inlineKeyboardButton.setCallbackData(StepsCenter.buildCallbackData(false, session, Command.Upgrade, "true"));

                        InlineKeyboardButton inlineKeyboardButton2 = new InlineKeyboardButton();
                        inlineKeyboardButton2.setText(I18nHandle.getText(session.getFromId(), I18nEnum.Cancel));
                        inlineKeyboardButton2.setCallbackData(StepsCenter.buildCallbackData(false, session, Command.Upgrade, "false"));

                        MessageHandle.sendInlineKeyboard(session.getChatId(), builder.toString(), inlineKeyboardButton, inlineKeyboardButton2);

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
                        MessageHandle.editMessage(message, I18nHandle.getText(session.getFromId(), I18nEnum.UnknownError));
                        return StepResult.end();
                    }
                })
                .steps((StepsChatSession session, int index, List<String> list, Map<String, Object> context) -> {
                    Boolean of = Boolean.valueOf(session.getText());
                    if (of) {
                        Message message = MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), I18nHandle.getText(session.getFromId(), I18nEnum.Updating), false);
                        String url = (String) context.get("url");

                        AtomicInteger count = new AtomicInteger();
                        String temp = System.getProperty("user.dir") + "/temp.jar";
                        log.info("temp: " + temp);
                        boolean b = DownloadUtil.download(
                                RequestProxyConfig.create(),
                                url,
                                temp,
                                (String var1, String var2, Long var3, Long var4) -> {
                                    if ((var4 - var3) > 0) {
                                        count.incrementAndGet();
                                        if (count.get() == 100) {
                                            MessageHandle.editMessage(message, I18nHandle.getText(session.getFromId(), I18nEnum.Downloaded, BytesUtil.toDisplayStr(var3), BytesUtil.toDisplayStr(var4)));
                                            count.set(0);
                                        }
                                    }
                                }
                        );

                        if (b) {
                            System.exit(1);
                        } else {
                            MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), I18nHandle.getText(session.getFromId(), I18nEnum.UnknownError), false);
                        }

                    } else {
                        MessageHandle.sendMessage(session.getChatId(), session.getReplyToMessageId(), I18nHandle.getText(session.getFromId(), I18nEnum.CancelSucceeded), false);
                    }
                    return StepResult.end();
                })
                .build();
    }

}
