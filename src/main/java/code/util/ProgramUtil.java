package code.util;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

@Slf4j
public class ProgramUtil {

    private final static String Dir = System.getProperty("user.dir");

    public static void restart(String processName) {
        try {
            Runtime.getRuntime().exec(new String[]{"java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "/" + processName});
            System.exit(0);
        } catch (IOException e) {
            log.error(ExceptionUtil.getStackTraceWithCustomInfoToStr(e));
        }
    }

}
