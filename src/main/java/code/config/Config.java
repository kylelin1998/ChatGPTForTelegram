package code.config;

import code.util.ExceptionUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONReader;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public class Config {

    private static String UserDir = System.getProperty("user.dir");

    public final static String CurrentDir = (UserDir.equals("/") ? "" : UserDir) + "/config";

    public static String SettingsPath = CurrentDir + "/config.json";

    public static String DBPath = CurrentDir + "/db.db";

    public static class MetaData {
        public final static String CurrentVersion = "1.0.0";
        public final static String GitOwner = "kylelin1998";
        public final static String GitRepo = "ChatGPTForTelegram";
        public final static String ProcessName = "app.jar";
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

    public synchronized static ConfigSettings readConfig() {
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
        }
        return null;
    }

//    public synchronized static boolean saveConfig(ConfigSettings configSettings) {
//        try {
//            File file = new File(SettingsPath);
//            FileUtils.write(file, JSON.toJSONString(configSettings, JSONWriter.Feature.PrettyFormat), StandardCharsets.UTF_8);
//            return true;
//        } catch (IOException e) {
//            log.error(ExceptionUtil.getStackTraceWithCustomInfoToStr(e));
//        }
//        return false;
//    }


}
