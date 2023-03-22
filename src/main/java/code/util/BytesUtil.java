package code.util;

import java.math.BigDecimal;

public class BytesUtil {

    private final static int KB = 1024;
    private final static int MB = 1024 * 1024;
    private final static int GB = 1024 * 1024 * 1024;

    private static BigDecimal divide(long size, int unit) {
        return new BigDecimal(size).divide(new BigDecimal(unit)).setScale(2, BigDecimal.ROUND_DOWN);
    }

    public static String toDisplayStr(long size) {
        if (size < KB) {
            return size + "B";
        } else if (size >= KB && size < MB) {
            return divide(size, KB) + "KB";
        } else if (size >= MB && size < GB) {
            return divide(size, MB) + "MB";
        } else if (size >= GB) {
            return divide(size, GB) + "GB";
        }
        return null;
    }

}
