package media;

import javax.sound.sampled.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * RTPRecorder.java
 * ใช้บันทึกเสียงจากไมโครโฟนหรือ RTP Input
 * และบันทึกไฟล์ไว้ใน D:\CRM_Data\media\
 */
public class RTPRecorder {

    private TargetDataLine microphone;
    private AudioFormat audioFormat;
    private Thread recordThread;
    private boolean recording = false;
    private File outputFile;

    public RTPRecorder() {
        // === กำหนด path หลัก ===
        String basePath = "D:\\CRM_Data\\media";

        // ตรวจสอบและสร้างโฟลเดอร์ถ้ายังไม่มี
        File dir = new File(basePath);
        if (!dir.exists()) {
            if (dir.mkdirs()) {
                System.out.println("สร้างโฟลเดอร์ใหม่: " + dir.getAbsolutePath());
            } else {
                System.err.println("ไม่สามารถสร้างโฟลเดอร์: " + dir.getAbsolutePath());
            }
        }

        // === สร้างชื่อไฟล์แบบวันที่เวลา ===
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String fileName = "call_" + timestamp + ".wav";

        this.outputFile = new File(dir, fileName);
        this.audioFormat = getAudioFormat();
    }

    private AudioFormat getAudioFormat() {
        float sampleRate = 8000.0F;  // 8 kHz สำหรับเสียงโทรศัพท์
        int sampleSizeInBits = 16;
        int channels = 1;            // Mono
        boolean signed = true;
        boolean bigEndian = false;
        return new AudioFormat(sampleRate, sampleSizeInBits, channels, signed, bigEndian);
    }

    /** เริ่มบันทึกเสียง */
    public void start() {
        try {
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, audioFormat);
            if (!AudioSystem.isLineSupported(info)) {
                System.err.println("ไม่รองรับอุปกรณ์บันทึกเสียง");
                return;
            }

            microphone = (TargetDataLine) AudioSystem.getLine(info);
            microphone.open(audioFormat);
            microphone.start();

            recording = true;
            recordThread = new Thread(() -> {
                try (AudioInputStream ais = new AudioInputStream(microphone)) {
                    System.out.println("เริ่มบันทึกเสียงที่: " + outputFile.getAbsolutePath());
                    AudioSystem.write(ais, AudioFileFormat.Type.WAVE, outputFile);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });

            recordThread.start();

        } catch (LineUnavailableException e) {
            e.printStackTrace();
        }
    }

    /** หยุดบันทึกเสียง */
    public void stop() {
        recording = false;
        if (microphone != null) {
            microphone.stop();
            microphone.close();
        }
        System.out.println("หยุดบันทึกเสียงแล้ว: " + outputFile.getAbsolutePath());
    }

    public boolean isRecording() {
        return recording;
    }
}
