package code.util.ffmpeg;

import lombok.Getter;

import java.util.Properties;

@Getter
public enum FfmpegDownloadUrl {

    Windows("https://github.com/BtbN/FFmpeg-Builds/releases/download/latest/ffmpeg-n6.0-latest-win64-gpl-6.0.zip"),

    LINUX_AMD64("https://johnvansickle.com/ffmpeg/builds/ffmpeg-git-amd64-static.tar.xz"),
    LINUX_I686("https://johnvansickle.com/ffmpeg/builds/ffmpeg-git-i686-static.tar.xz"),
    LINUX_ARM64("https://johnvansickle.com/ffmpeg/builds/ffmpeg-git-arm64-static.tar.xz"),
    LINUX_ARMHF("https://johnvansickle.com/ffmpeg/builds/ffmpeg-git-armhf-static.tar.xz"),
    LINUX_ARMEL("https://johnvansickle.com/ffmpeg/builds/ffmpeg-git-armel-static.tar.xz"),

    ;

    private String url;

    FfmpegDownloadUrl(String url) {
        this.url = url;
    }

    public static FfmpegDownloadUrl getFfmpegDownloadUrl() {
        Properties properties = System.getProperties();
        String name = properties.getProperty("os.name");
        String arch = properties.getProperty("os.arch");

        if (name.toLowerCase().contains("windows")) {
            return Windows;
        } else {
            if ("amd64".equals(arch)) {
                return LINUX_AMD64;
            } else if ("i686".equals(arch)) {
                return LINUX_I686;
            } else if ("arm64".equals(arch)) {
                return LINUX_ARM64;
            } else if ("armhf".equals(arch)) {
                return LINUX_ARMHF;
            } else if ("armel".equals(arch)) {
                return LINUX_ARMEL;
            } else {
                return LINUX_AMD64;
            }
        }
    }

}
