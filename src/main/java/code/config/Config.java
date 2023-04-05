package code.config;

import code.util.ExceptionUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONException;
import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.JSONWriter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

@Slf4j
public class Config {

    private static String UserDir = System.getProperty("user.dir");

    public final static String CurrentDir = (UserDir.equals("/") ? "" : UserDir) + "/config";

    public static String SettingsPath = CurrentDir + "/config.json";

    public static String DBPath = CurrentDir + "/db.db";

    private static ReentrantReadWriteLock reentrantReadWriteLock = new ReentrantReadWriteLock();

    public static class MetaData {
        public final static String CurrentVersion = "1.0.8";
        public final static String GitOwner = "kylelin1998";
        public final static String GitRepo = "ChatGPTForTelegram";
        public final static String ProcessName = "ChatGPTForTelegram-universal.jar";
        public final static String JarName = "ChatGPTForTelegram-universal.jar";
    }

    static {
        mkdirs(CurrentDir);

        List<String> list = new ArrayList<>();
        list.add(UserDir);
        list.add(CurrentDir);
        list.add(SettingsPath);
        log.info(list.stream().collect(Collectors.joining("\n")));
    }

    private static void mkdirs(String... path) {
        for (String s : path) {
            File file = new File(s);
            if (!file.exists()) {
                file.mkdirs();
            }
        }
    }

    public static ConfigSettings readConfig() {
        ReentrantReadWriteLock.ReadLock readLock = reentrantReadWriteLock.readLock();
        readLock.lock();
        try {
            File file = new File(SettingsPath);
            boolean exists = file.exists();
            if (exists) {
                String text = FileUtils.readFileToString(file, StandardCharsets.UTF_8);
                ConfigSettings configSettings = JSON.parseObject(text, ConfigSettings.class, JSONReader.Feature.SupportSmartMatch);
                return configSettings;
            } else {
                log.warn("Settings file not found, " + SettingsPath);
            }
        } catch (IOException e) {
            log.error(ExceptionUtil.getStackTraceWithCustomInfoToStr(e), SettingsPath);
        } finally {
            readLock.unlock();
        }
        return null;
    }

    public static ConfigSettings verifyConfig(String configJson) {
        if (StringUtils.isBlank(configJson)) {
            return null;
        }
        ConfigSettings configSettings = null;
        try {
            configSettings = JSON.parseObject(configJson, ConfigSettings.class, JSONReader.Feature.SupportSmartMatch);
        } catch (JSONException e) {
        }
        if (null == configSettings) {
            return null;
        }
        for (Field field : configSettings.getClass().getDeclaredFields()) {
            ConfigField configField = field.getAnnotation(ConfigField.class);
            if (null == configField) {
                continue;
            }
            if (configField.isNotNull()) {
                try {
                    field.setAccessible(true);
                    Object o = field.get(configSettings);
                    if (null == o) {
                        return null;
                    }
                } catch (IllegalAccessException e) {
                    log.error(ExceptionUtil.getStackTraceWithCustomInfoToStr(e));
                    return null;
                }
            }
        }

        return configSettings;
    }

    public static boolean saveConfig(ConfigSettings configSettings) {
        ReentrantReadWriteLock.WriteLock writeLock = reentrantReadWriteLock.writeLock();
        writeLock.lock();
        try {
            File file = new File(SettingsPath);
            FileUtils.write(file, JSON.toJSONString(configSettings, JSONWriter.Feature.PrettyFormat), StandardCharsets.UTF_8);
            return true;
        } catch (IOException e) {
            log.error(ExceptionUtil.getStackTraceWithCustomInfoToStr(e));
        } finally {
            writeLock.unlock();
        }
        return false;
    }

}
