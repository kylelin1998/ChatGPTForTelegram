package code.util;

import code.config.RequestProxyConfig;
import kong.unirest.GetRequest;
import kong.unirest.HttpResponse;
import kong.unirest.ProgressMonitor;
import kong.unirest.Unirest;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.fluent.Request;
import org.apache.hc.core5.util.Timeout;

import java.io.File;
import java.io.InputStream;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.TimeUnit;

@Slf4j
public class DownloadUtil {

    public static InputStream download(RequestProxyConfig requestProxyConfig, String url) {
        try {
            Request request = Request
                    .get(url)
                    .connectTimeout(Timeout.ofSeconds(30))
                    .responseTimeout(Timeout.ofSeconds(60));
            requestProxyConfig.viaProxy(request);
            return request.execute().returnContent().asStream();

        } catch (Exception e) {
            log.error(ExceptionUtil.getStackTraceWithCustomInfoToStr(e));
        }
        return null;
    }

    public static boolean download(RequestProxyConfig requestProxyConfig, String url, String file, ProgressMonitor progressMonitor) {
        try {
            GetRequest request = Unirest
                    .get(url)
                    .downloadMonitor(progressMonitor)
                    .connectTimeout((int) TimeUnit.MILLISECONDS.convert(30, TimeUnit.SECONDS))
                    ;
            requestProxyConfig.viaProxy(request);

            HttpResponse<File> response = request.asFile(file, StandardCopyOption.REPLACE_EXISTING);
            return response.getStatus() == 200;
        } catch (Exception e) {
            log.error(ExceptionUtil.getStackTraceWithCustomInfoToStr(e));
        }
        return false;
    }

}
