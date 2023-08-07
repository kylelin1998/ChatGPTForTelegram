package code.util;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class ThreadUtil {

    private static String name = "Thread-Util-";
    private static AtomicInteger atomicInteger = new AtomicInteger(0);

    public interface IntervalCallback {
        void run();
    }

    public static synchronized void newIntervalWithTryCatch(IntervalCallback callback, int unit, TimeUnit timeUnit) {
        new Thread(() -> {
            while (true) {
                try {
                    callback.run();
                } catch (Exception e) {
                    log.error(ExceptionUtil.getStackTraceWithCustomInfoToStr(e));
                }

                try {
                    timeUnit.sleep(unit);
                } catch (InterruptedException e) {
                    log.error(ExceptionUtil.getStackTraceWithCustomInfoToStr(e));
                    throw new RuntimeException(e);
                }
            }
        }, name + atomicInteger.incrementAndGet()).start();
    }

}
