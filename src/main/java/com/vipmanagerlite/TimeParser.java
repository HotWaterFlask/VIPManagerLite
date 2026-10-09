package com.vipmanagerlite;

/**
 * 时间解析工具类
 * 解析时间字符串（如 30d, 2h, 1w）为毫秒数
 */
public class TimeParser {

    /**
     * 解析时间字符串为毫秒数
     * 支持格式：
     * - s: 秒 (如 30s = 30秒)
     * - m: 分钟 (如 30m = 30分钟)
     * - h: 小时 (如 2h = 2小时)
     * - d: 天 (如 7d = 7天)
     * - w: 周 (如 2w = 2周)
     * - M: 月 (如 1M = 30天)
     * - y: 年 (如 1y = 365天)
     *
     * @param timeStr 时间字符串
     * @return 毫秒数，解析失败返回 -1
     */
    public static long parseTime(String timeStr) {
        if (timeStr == null || timeStr.isEmpty()) {
            return -1;
        }

        timeStr = timeStr.trim();
        if (timeStr.isEmpty()) {
            return -1;
        }

        try {
            char unit = timeStr.charAt(timeStr.length() - 1);
            String numberPart = timeStr.substring(0, timeStr.length() - 1);
            long value = Long.parseLong(numberPart);

            // 非法时长（负数或 0）视为解析失败
            if (value <= 0) {
                return -1;
            }

            return switch (unit) {
                case 's' -> value * 1000;                           // 秒
                case 'm' -> value * 60 * 1000;                      // 分钟
                case 'h' -> value * 60 * 60 * 1000;                 // 小时
                case 'd' -> value * 24 * 60 * 60 * 1000;            // 天
                case 'w' -> value * 7 * 24 * 60 * 60 * 1000;        // 周
                case 'M' -> value * 30 * 24 * 60 * 60 * 1000;       // 月（按30天算）
                case 'y' -> value * 365 * 24 * 60 * 60 * 1000;      // 年（按365天算）
                default -> -1;
            };
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * 格式化时间为可读字符串
     *
     * @param milliseconds 毫秒数
     * @return 格式化后的时间字符串
     */
    public static String formatTime(long milliseconds) {
        if (milliseconds <= 0) {
            return "已过期";
        }

        long seconds = milliseconds / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (days > 0) {
            return days + "天 " + (hours % 24) + "小时";
        } else if (hours > 0) {
            return hours + "小时 " + (minutes % 60) + "分钟";
        } else if (minutes > 0) {
            return minutes + "分钟 " + (seconds % 60) + "秒";
        } else {
            return seconds + "秒";
        }
    }
}
