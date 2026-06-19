import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ExtractZip extends JFrame {
    private JTextField zipPathField;
    private JTextField extractDirField;
    private JButton browseZipButton;
    private JButton browseDirButton;
    private JButton extractButton;
    private JButton cancelButton;
    private JTextArea logArea;
    private JProgressBar progressBar;
    private JLabel statusLabel;
    private volatile boolean isCancelled = false;

    public ExtractZip() {
        initUI();
    }

    private void initUI() {
        setTitle("Zip解压工具");
        setSize(600, 500);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        // 文件选择面板
        JPanel filePanel = new JPanel();
        filePanel.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Zip文件选择
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0;
        filePanel.add(new JLabel("Zip文件:"), gbc);

        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.weightx = 1;
        zipPathField = new JTextField();
        filePanel.add(zipPathField, gbc);

        gbc.gridx = 2;
        gbc.gridy = 0;
        gbc.weightx = 0;
        browseZipButton = new JButton("浏览...");
        filePanel.add(browseZipButton, gbc);

        // 输出目录选择
        gbc.gridx = 0;
        gbc.gridy = 1;
        filePanel.add(new JLabel("输出目录:"), gbc);

        gbc.gridx = 1;
        gbc.gridy = 1;
        extractDirField = new JTextField();
        filePanel.add(extractDirField, gbc);

        gbc.gridx = 2;
        gbc.gridy = 1;
        browseDirButton = new JButton("浏览...");
        filePanel.add(browseDirButton, gbc);

        mainPanel.add(filePanel, BorderLayout.NORTH);

        // 日志区域
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane scrollPane = new JScrollPane(logArea);
        scrollPane.setBorder(BorderFactory.createTitledBorder("解压日志"));
        mainPanel.add(scrollPane, BorderLayout.CENTER);

        // 底部面板
        JPanel bottomPanel = new JPanel();
        bottomPanel.setLayout(new BorderLayout(10, 10));

        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        bottomPanel.add(progressBar, BorderLayout.NORTH);

        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new FlowLayout(FlowLayout.CENTER, 10, 10));

        extractButton = new JButton("开始解压");
        extractButton.setPreferredSize(new Dimension(120, 35));
        buttonPanel.add(extractButton);

        cancelButton = new JButton("取消");
        cancelButton.setPreferredSize(new Dimension(120, 35));
        cancelButton.setEnabled(false);
        buttonPanel.add(cancelButton);

        statusLabel = new JLabel("准备就绪");
        statusLabel.setHorizontalAlignment(SwingConstants.CENTER);
        bottomPanel.add(statusLabel, BorderLayout.CENTER);
        bottomPanel.add(buttonPanel, BorderLayout.SOUTH);

        mainPanel.add(bottomPanel, BorderLayout.SOUTH);

        add(mainPanel);

        // 事件监听
        browseZipButton.addActionListener(new BrowseZipListener());
        browseDirButton.addActionListener(new BrowseDirListener());
        extractButton.addActionListener(new ExtractListener());
        cancelButton.addActionListener(new CancelListener());
    }

    private class BrowseZipListener implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileFilter(new javax.swing.filechooser.FileFilter() {
                @Override
                public boolean accept(File f) {
                    return f.isDirectory() || f.getName().toLowerCase().endsWith(".zip");
                }

                @Override
                public String getDescription() {
                    return "Zip文件 (*.zip)";
                }
            });
            int result = chooser.showOpenDialog(ExtractZip.this);
            if (result == JFileChooser.APPROVE_OPTION) {
                zipPathField.setText(chooser.getSelectedFile().getAbsolutePath());
            }
        }
    }

    private class BrowseDirListener implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            int result = chooser.showOpenDialog(ExtractZip.this);
            if (result == JFileChooser.APPROVE_OPTION) {
                extractDirField.setText(chooser.getSelectedFile().getAbsolutePath());
            }
        }
    }

    private class CancelListener implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            isCancelled = true;
            log("用户取消操作");
            statusLabel.setText("正在取消...");
            cancelButton.setEnabled(false);
        }
    }

    private class ExtractListener implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            final String zipPath = zipPathField.getText().trim();
            final String extractDir = extractDirField.getText().trim();

            if (zipPath.isEmpty()) {
                JOptionPane.showMessageDialog(ExtractZip.this, "请选择Zip文件", "错误", JOptionPane.ERROR_MESSAGE);
                return;
            }

            if (extractDir.isEmpty()) {
                JOptionPane.showMessageDialog(ExtractZip.this, "请选择输出目录", "错误", JOptionPane.ERROR_MESSAGE);
                return;
            }

            File zipFile = new File(zipPath);
            if (!zipFile.exists()) {
                JOptionPane.showMessageDialog(ExtractZip.this, "Zip文件不存在", "错误", JOptionPane.ERROR_MESSAGE);
                return;
            }

            isCancelled = false;
            extractButton.setEnabled(false);
            browseZipButton.setEnabled(false);
            browseDirButton.setEnabled(false);
            cancelButton.setEnabled(true);
            logArea.setText("");
            progressBar.setValue(0);
            statusLabel.setText("正在解压...");

            new Thread(() -> {
                try {
                    extractZip(zipPath, extractDir);
                    if (!isCancelled) {
                        statusLabel.setText("解压完成！");
                    }
                } catch (Exception ex) {
                    if (!isCancelled) {
                        log("错误: " + ex.getMessage());
                        ex.printStackTrace();
                        statusLabel.setText("解压失败");
                    }
                } finally {
                    extractButton.setEnabled(true);
                    browseZipButton.setEnabled(true);
                    browseDirButton.setEnabled(true);
                    cancelButton.setEnabled(false);
                }
            }).start();
        }
    }

    private void extractZip(String zipPath, String extractDir) throws Exception {
        log("开始解压: " + zipPath);
        log("输出目录: " + extractDir);
        log("输出目录长度: " + extractDir.length());
        
        File extractDirFile = new File(extractDir);
        log("输出目录绝对路径: " + extractDirFile.getAbsolutePath());
        log("输出目录是否存在: " + extractDirFile.exists());
        log("输出目录是否可写: " + extractDirFile.canWrite());
        
        if (!extractDirFile.exists()) {
            log("输出目录不存在，尝试创建...");
            boolean created = extractDirFile.mkdirs();
            log("创建结果: " + created);
            if (!created) {
                throw new IOException("无法创建输出目录: " + extractDir + " (请检查权限或路径长度)");
            }
            log("成功创建输出目录: " + extractDir);
        } else {
            log("输出目录已存在");
        }

        ZipFile zipFile = new ZipFile(zipPath);
        Enumeration<? extends ZipEntry> entries = zipFile.entries();
        
        log("ZIP文件条目数量: " + zipFile.size());

        int totalEntries = 0;
        while (entries.hasMoreElements()) {
            entries.nextElement();
            totalEntries++;
        }

        entries = zipFile.entries();
        int processedCount = 0;
        int fileCount = 0;
        int dirCount = 0;
        int fixedCount = 0;
        int failedCount = 0;

        while (entries.hasMoreElements()) {
            if (isCancelled) {
                log("检测到取消操作，停止解压");
                break;
            }
            
            ZipEntry entry = entries.nextElement();
            String entryName = entry.getName();

            boolean isDirectory = entry.isDirectory();
            long compressedSize = entry.getCompressedSize();

            String outputName = entryName;
            if (outputName.endsWith("/")) {
                outputName = outputName.substring(0, outputName.length() - 1);
            }

            File outputFile = new File(extractDir, outputName);

            try {
                if (isDirectory && compressedSize > 0) {
                    log("修复: 将目录转换为文件 - " + entryName);

                    File parentDir = outputFile.getParentFile();
                    if (parentDir != null && !parentDir.exists()) {
                        if (!parentDir.mkdirs()) {
                            log("警告: 无法创建目录 - " + parentDir.getAbsolutePath());
                            failedCount++;
                            processedCount++;
                            continue;
                        }
                    }

                    try (InputStream inputStream = zipFile.getInputStream(entry);
                         FileOutputStream outputStream = new FileOutputStream(outputFile)) {

                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        while ((bytesRead = inputStream.read(buffer)) != -1) {
                            outputStream.write(buffer, 0, bytesRead);
                        }
                    }

                    fileCount++;
                    fixedCount++;
                } else if (isDirectory) {
                    if (!outputFile.exists()) {
                        if (!outputFile.mkdirs()) {
                            log("警告: 无法创建目录 - " + outputFile.getAbsolutePath());
                            failedCount++;
                            processedCount++;
                            continue;
                        }
                    }
                    dirCount++;
                } else {
                    File parentDir = outputFile.getParentFile();
                    if (parentDir != null && !parentDir.exists()) {
                        if (!parentDir.mkdirs()) {
                            log("警告: 无法创建目录 - " + parentDir.getAbsolutePath());
                            failedCount++;
                            processedCount++;
                            continue;
                        }
                    }

                    try (InputStream inputStream = zipFile.getInputStream(entry);
                         FileOutputStream outputStream = new FileOutputStream(outputFile)) {

                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        while ((bytesRead = inputStream.read(buffer)) != -1) {
                            outputStream.write(buffer, 0, bytesRead);
                        }
                    }

                    fileCount++;
                }
            } catch (Exception e) {
                log("错误: 无法解压文件 " + entryName + " - " + e.getMessage());
                failedCount++;
            }

            processedCount++;
            int progress = processedCount * 100 / totalEntries;
            progressBar.setValue(progress);

            if (fileCount % 1000 == 0) {
                log("已解压 " + fileCount + " 个文件...");
            }
        }

        zipFile.close();
        if (isCancelled) {
            log("已取消！已解压 " + fileCount + " 个文件，" + dirCount + " 个目录，修复了 " + fixedCount + " 个错误标记的文件，失败 " + failedCount + " 个");
            SwingUtilities.invokeLater(() -> statusLabel.setText("已取消"));
        } else {
            log("完成！共解压 " + fileCount + " 个文件，" + dirCount + " 个目录，修复了 " + fixedCount + " 个错误标记的文件，失败 " + failedCount + " 个");
        }
    }

    private void log(String message) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(message + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    static void main() {
        SwingUtilities.invokeLater(() -> {
            ExtractZip app = new ExtractZip();
            app.setVisible(true);
        });
    }
}