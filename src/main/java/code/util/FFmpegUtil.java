package code.util;

import lombok.extern.slf4j.Slf4j;
import org.glassfish.jersey.internal.guava.ThreadFactoryBuilder;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

@Slf4j
public class FFmpegUtil {

    private static ThreadFactory threadFactory = new ThreadFactoryBuilder().setDaemon(false).setNameFormat("ffmpeg-pool-%d").build();

    private static ExecutorService fixedThreadPool = new ThreadPoolExecutor(
            5,
            20,
            60,
            TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(200),
            threadFactory,
            (Runnable r, ThreadPoolExecutor executor) -> {
                log.error(r.toString() + " is Rejected");
            }
    );
    public static void oggFileToMp3(String ffmpegPath, String filePath, String outPath) {
        List<String> commend = new ArrayList<String>();
        commend.add(ffmpegPath);
        commend.add("-i");
        commend.add(filePath);
        commend.add("-acodec");
        commend.add("libmp3lame");
        commend.add("-y");
        commend.add(outPath);

        try {
            ProcessBuilder builder = new ProcessBuilder(commend);
            builder.command(commend);
            log.info("ffmpeg commend: {}", String.join(" ", commend));
            Process p = builder.start();

            // 获取外部程序标准输出流
            fixedThreadPool.submit(new OutputHandlerRunnable(p.getInputStream(), false));
            // 获取外部程序标准错误流
            fixedThreadPool.submit(new OutputHandlerRunnable(p.getErrorStream(), true));
            int code = p.waitFor();
            log.info("ffmpeg commend: {} result: {}", String.join(" ", commend), code);

            p.destroy();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    private static class OutputHandlerRunnable implements Runnable {
        private InputStream in;

        private boolean error;

        public OutputHandlerRunnable(InputStream in, boolean error) {
            this.in = in;
            this.error = error;
        }

        @Override
        public void run() {
            try (BufferedReader bufr = new BufferedReader(new InputStreamReader(this.in))) {
                String line = null;
                while ((line = bufr.readLine()) != null) {
                    if (error) {
                        System.out.println(line);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

}
