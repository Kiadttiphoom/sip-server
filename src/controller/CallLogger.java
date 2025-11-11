package controller;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * CallLogger.java
 * ใช้บันทึกประวัติการโทรลงไฟล์ CSV ที่ D:\CRM_Data\logs\call_log.csv
 */
public class CallLogger {

    private final File logFile;

    public CallLogger() {
        // === สร้างโฟลเดอร์ถ้ายังไม่มี ===
        File logDir = new File("D:\\CRM_Data\\logs");
        if (!logDir.exists()) {
            if (logDir.mkdirs()) {
                System.out.println("สร้างโฟลเดอร์ใหม่: " + logDir.getAbsolutePath());
            } else {
                System.err.println("ไม่สามารถสร้างโฟลเดอร์ log");
            }
        }

        this.logFile = new File(logDir, "call_log.csv");

        // ถ้ายังไม่มีไฟล์ > เขียนหัวตาราง CSV
        if (!logFile.exists()) {
            try (PrintWriter writer = new PrintWriter(new FileWriter(logFile, true))) {
                writer.println("DateTime,Caller,Receiver,Status,Duration,AudioFile");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * บันทึกข้อมูลการโทร 1 รายการลงใน log
     */
    public void logCall(String caller, String receiver, String status, long durationMillis, String audioFilePath) {
        String datetime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        String durationStr = formatDuration(durationMillis);

        try (PrintWriter writer = new PrintWriter(new FileWriter(logFile, true))) {
            writer.printf("%s,%s,%s,%s,%s,%s%n",
                    datetime, caller, receiver, status, durationStr, audioFilePath);
        } catch (IOException e) {
            e.printStackTrace();
        }

        System.out.println("บันทึกข้อมูลการโทรแล้ว: " + caller + " > " + receiver);
    }

    private String formatDuration(long millis) {
        long seconds = millis / 1000;
        long mins = seconds / 60;
        long secs = seconds % 60;
        return String.format("%02d:%02d", mins, secs);
    }
}
