package code.util;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
public class ProgramUtil {

    public static void restart(String processName) {
        try {
            String[] strings = {"java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "/" + processName};
            log.info(Stream.of(strings).collect(Collectors.joining(" ")));
            Runtime.getRuntime().exec(strings);
            System.exit(1);
        } catch (IOException e) {
            log.error(ExceptionUtil.getStackTraceWithCustomInfoToStr(e));
        }
    }

}
