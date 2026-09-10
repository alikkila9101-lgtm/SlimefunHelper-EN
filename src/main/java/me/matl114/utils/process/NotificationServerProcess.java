package me.matl114.utils.process;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// copied from https://github.com/SnowZhouer/queue-notice-mod
public class NotificationServerProcess {

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: NotificationServer <title> <message>");
            System.exit(1);
            return;
        }

        String title = args[0];
        String message = args[1];
        System.out.println("[NotificationServer] Send notification: title=" + title + ", message=" + message);

        // 检查 SystemTray 支持
        if (!SystemTray.isSupported() || SystemTray.getSystemTray() == null) {
            System.err.println("[NotificationServer] SystemTray not supported");
            System.exit(1);
            return;
        }

        TrayIcon trayIcon = null;
        try {
            // 创建简单图标
            BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2d = image.createGraphics();
            g2d.setColor(Color.GREEN);
            g2d.fillOval(0, 0, 16, 16);
            g2d.dispose();

            trayIcon = new TrayIcon(image, "Queue Notice Mod");
            trayIcon.setImageAutoSize(true);
            SystemTray.getSystemTray().add(trayIcon);

            // 显示通知
            trayIcon.displayMessage(title, message, TrayIcon.MessageType.INFO);
            System.out.println("[NotificationServer] Notification sent");

            // 保持进程存活一段时间，确保通知显示完成
            Thread.sleep(6000);
        } catch (Exception e) {
            System.err.println("[NotificationServer] Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        } finally {
            if (trayIcon != null) {
                try {
                    SystemTray.getSystemTray().remove(trayIcon);
                } catch (Exception ignored) {
                }
            }
        }
        System.out.println("[NotificationServer] Done, exiting");
        System.exit(0);
    }

    public static class Bootstrap {
        private static final Logger LOGGER = LoggerFactory.getLogger("QueueNotice-Notification");
        private static String javaPath;

        static {
            LOGGER.info("[NotificationHelper] Initializing notification system (subprocess approach)...");

            // 查找 javaw.exe（无控制台窗口的 Java）
            String javaHome = System.getProperty("java.home");
            javaPath = javaHome + "/bin/javaw.exe";

            // 验证 javaw.exe 存在
            try {
                ProcessBuilder pb = new ProcessBuilder(javaPath, "-version");
                pb.redirectErrorStream(true);
                Process proc = pb.start();
                proc.waitFor();
                LOGGER.info("[NotificationHelper] Java subprocess available: {}", javaPath);
            } catch (Exception e) {
                // 回退到 java.exe
                javaPath = javaHome + "/bin/java.exe";
                try {
                    ProcessBuilder pb = new ProcessBuilder(javaPath, "-version");
                    pb.redirectErrorStream(true);
                    Process proc = pb.start();
                    proc.waitFor();
                    LOGGER.info("[NotificationHelper] Java subprocess available (fallback): {}", javaPath);
                } catch (Exception e2) {
                    LOGGER.error("[NotificationHelper] Cannot find Java executable, notification features unavailable");
                    javaPath = null;
                }
            }
        }

        public static boolean notify(String title, String message) {
            if (javaPath == null) {
                return false;
            }

            // 异步发送，不阻塞游戏线程
            Thread notifyThread = new Thread(() -> sendViaSubprocess(title, message), "QueueNotice-Send");
            notifyThread.setDaemon(true);
            notifyThread.start();
            return true;
        }

        private static void sendViaSubprocess(String title, String message) {
            try {
                String modClasspath = getModClasspath();
                if (modClasspath == null || modClasspath.isEmpty()) {
                    return;
                }
                String serverClass = NotificationServerProcess.class.getName();

                List<String> cmd = new ArrayList<>();
                cmd.add(javaPath);
                cmd.add("-cp");
                cmd.add(modClasspath);
                cmd.add(serverClass);
                cmd.add(title);
                cmd.add(message);

                ProcessBuilder pb = new ProcessBuilder(cmd);
                pb.redirectErrorStream(true);
                Process proc = pb.start();

                // 等待子进程完成（最多 15 秒）
                boolean finished = proc.waitFor(15, java.util.concurrent.TimeUnit.SECONDS);
                if (finished) {
                    int exitCode = proc.exitValue();
                    if (exitCode == 0) {
                        LOGGER.info("[NotificationHelper] Notification subprocess exited normally");
                    } else {
                        LOGGER.warn("[NotificationHelper] Notification subprocess exit code: {}", exitCode);
                    }
                } else {
                    LOGGER.warn("[NotificationHelper] Notification subprocess timed out, force terminating");
                    proc.destroyForcibly();
                }
            } catch (Exception e) {
                LOGGER.error("[NotificationHelper] Notification subprocess failed to start: {}", e.getMessage(), e);
            }
        }
    }

    private static String getModClasspath() {
        try {
            URI location = Bootstrap.class
                    .getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .toURI();
            File file = new File(location);
            return file.getAbsolutePath();
        } catch (Exception e) {
            return null;
        }
    }
}
