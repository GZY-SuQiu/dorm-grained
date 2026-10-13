package com.dorm.duty;

import android.content.Context;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.io.StringWriter;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 崩溃日志：把未捕获异常的堆栈追加写到外部私有目录 crash.log，
 * 用户挂载 /sdcard/Android/data/com.dorm.duty/files 后即可离线分析。
 * 文件超过 8KB 时截断（保留最早的 8KB，最新日志在前）。
 */
public class CrashLog {

    private static File file(Context c) {
        File dir = c.getExternalFilesDir(null);
        if (dir == null) dir = c.getFilesDir();
        return new File(dir, "crash.log");
    }

    /** 追加一条崩溃记录（时间 + 完整堆栈），失败静默。 */
    public static void append(Context c, Throwable t) {
        try {
            File f = file(c);
            f.getParentFile().mkdirs();
            StringWriter sw = new StringWriter();
            t.printStackTrace(new PrintWriter(sw));
            String ts = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
            String body = "===== " + ts + " =====\n" + sw + "\n";
            synchronized (CrashLog.class) {
                try (BufferedWriter w = new BufferedWriter(new FileWriter(f, true))) {
                    w.write(body);
                }
                if (f.length() > 8000) {
                    RandomAccessFile raf = new RandomAccessFile(f, "rw");
                    try { raf.setLength(8000); } finally { raf.close(); }
                }
            }
        } catch (Exception ignored) {}
    }

    /** 读取最后一条崩溃记录（含堆栈），无则返回空串。 */
    public static String last(Context c) {
        try {
            File f = file(c);
            if (!f.exists()) return "";
            String all = new String(Files.readAllBytes(f.toPath())).trim();
            int idx = all.lastIndexOf("\n=====");
            return idx >= 0 ? all.substring(idx + 1).trim() : all;
        } catch (Exception e) {
            return "";
        }
    }

    /** 最近崩溃的一行摘要（异常类名 + 消息），用于界面上展示。 */
    public static String lastSummary(Context c) {
        String s = last(c);
        if (s.isEmpty()) return "";
        String[] lines = s.split("\n");
        for (String l : lines) {
            if (l.startsWith("java.") || l.contains("Exception") || l.contains("Error")) {
                return l.trim();
            }
        }
        return lines.length > 1 ? lines[1].trim() : s;
    }
}
