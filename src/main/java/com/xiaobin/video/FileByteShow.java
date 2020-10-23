package com.xiaobin.video;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

public class FileByteShow {

    private final static Map<Integer, String> PREFIX_BINARY_ZERO_MAP = new HashMap<>();
    private final static Map<Integer, String> PREFIX_HEX_ZERO_MAP = new HashMap<>();

    static{
        PREFIX_BINARY_ZERO_MAP.put(0, "00000000");
        PREFIX_BINARY_ZERO_MAP.put(1, "0000000");
        PREFIX_BINARY_ZERO_MAP.put(2, "000000");
        PREFIX_BINARY_ZERO_MAP.put(3, "00000");
        PREFIX_BINARY_ZERO_MAP.put(4, "0000");
        PREFIX_BINARY_ZERO_MAP.put(5, "000");
        PREFIX_BINARY_ZERO_MAP.put(6, "00");
        PREFIX_BINARY_ZERO_MAP.put(7, "0");
        PREFIX_BINARY_ZERO_MAP.put(8, "");

        PREFIX_HEX_ZERO_MAP.put(0, "00");
        PREFIX_HEX_ZERO_MAP.put(1, "0");
        PREFIX_HEX_ZERO_MAP.put(2, "");
    }
    private byte[] bytes;
    private final int width = 800;
    private final int height = 500;
    private final int filePanelHeight = 30;
    private final int btnPanelHeight = 30;
    private JFrame fileChooseFrame;
    private final int rows = 20;
    private final int byteInLine = 4;
    private int pageNum = 0;
    private final int pageSize = this.byteInLine * this.rows;
    private File selectedFile;
    private JTextArea binaryTextArea;
    private JTextArea hexTextArea;
    private JTextArea asciiTextArea;

    public static void main(String[] args) {
        FileByteShow main = new FileByteShow();
        main.show(main);
    }

    private void show(FileByteShow main){
        JFrame frame = new JFrame();
        frame.setSize(this.width, this.height);
        frame.setLocationRelativeTo(null);
        frame.setResizable(true);
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        frame.setLayout(new BorderLayout());
        frame.addWindowListener(new CustomWindowListener(main));

        initFilePanel(frame);
        initBytePanel(frame);
        initBtnPanel(frame);

        frame.setVisible(true);
    }

    private String formatBinary(String binary){
        if(binary.length() > 8){
            return binary.substring(binary.length() - 8);
        }
        return PREFIX_BINARY_ZERO_MAP.get(binary.length()) + binary;
    }
    private String formatHex(String hex){
        if(hex.length() > 2){
            return hex.substring(hex.length() - 2);
        }
        return PREFIX_HEX_ZERO_MAP.get(hex.length()) + hex;
    }
    private void showBinaryInfo(){
        byte[] bytes = new byte[this.pageSize];
        System.arraycopy(this.bytes, this.pageNum * this.pageSize, bytes, 0, bytes.length);
        StringBuilder binaryString = new StringBuilder(), hexString = new StringBuilder(), asciiString = new StringBuilder();
        String space = " ", enter = "\n";
        int end = this.byteInLine - 1;
        for(int i=0;i< bytes.length; i++){
            byte b = bytes[i];
            if(b < 0){
                b += 256;
            }
            binaryString.append(formatBinary(Integer.toBinaryString(b)));
            hexString.append(formatHex(Integer.toHexString(b)));
            if(b>= 32 && b <= 126){
                asciiString.append((char)b);
            }else{
                asciiString.append(".");
            }
            if(i % this.byteInLine == end){
                if(i < bytes.length - 1){
                    binaryString.append(enter);
                    hexString.append(enter);
                }
            }else{
                binaryString.append(space);
                hexString.append(space);
            }
            if(i == bytes.length / 2){
                asciiString.append(enter);
            }
        }
        this.binaryTextArea.setText(binaryString.toString());
        this.hexTextArea.setText(hexString.toString());
        this.asciiTextArea.setText(asciiString.toString());
    }

    private void initFileChooseFrame(JLabel jLabel){
        fileChooseFrame = new JFrame();
        JFileChooser jFileChooser = new JFileChooser();
        jFileChooser.setDialogType(JFileChooser.OPEN_DIALOG);
        jFileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        jFileChooser.addActionListener(fileAction -> {
            File file = jFileChooser.getSelectedFile();
            if(file == null){
                return;
            }
            jLabel.setText(file.getName());
            this.selectedFile = jFileChooser.getSelectedFile();
            fileChooseFrame.setVisible(false);
            try {
                this.bytes = Files.readAllBytes(this.selectedFile.toPath());
            } catch (IOException ioException) {
                this.selectedFile = null;
                this.bytes = null;
                throw new RuntimeException(ioException.getMessage(), ioException);
            }
            this.pageNum = 0;
            showBinaryInfo();
        });

        fileChooseFrame.add(jFileChooser);
        fileChooseFrame.setLocationRelativeTo(null);
        fileChooseFrame.setSize(600, 300);
    }

    private void initFilePanel(JFrame frame){
        JPanel jPanel = new JPanel();
        jPanel.setSize(this.width, this.filePanelHeight);

        JLabel jLabel = new JLabel("");
        jLabel.setName("selectedFileName");
        initFileChooseFrame(jLabel);

        JButton fileChooseBtn = new JButton("选择文件");
        fileChooseBtn.addActionListener(e -> fileChooseFrame.setVisible(true));

        jPanel.add(fileChooseBtn);
        jPanel.add(jLabel);

        frame.add(jPanel, BorderLayout.NORTH);
    }

    private void initBytePanel(JFrame frame){
        int asciiHeight = 40, height = this.height - this.filePanelHeight - this.btnPanelHeight - asciiHeight;
        JPanel jPanel = new JPanel();
        jPanel.setLayout(new FlowLayout());
        jPanel.setSize(this.width - 20, height);

        JPanel binaryPanel = new JPanel();
        this.binaryTextArea = new JTextArea();
        this.binaryTextArea.setRows(this.rows);
        this.binaryTextArea.setColumns(this.byteInLine * 8 + this.byteInLine - 1);
        this.binaryTextArea.setEnabled(false);
        binaryPanel.add(binaryTextArea);

        JPanel hexPanel = new JPanel();
        this.hexTextArea = new JTextArea();
        this.hexTextArea.setRows(this.rows);
        this.hexTextArea.setColumns(this.byteInLine * 2 + this.byteInLine - 1);
        this.hexTextArea.setEnabled(false);
        hexPanel.add(this.hexTextArea);

        JPanel asciiPanel = new JPanel();
        this.asciiTextArea = new JTextArea();
        this.asciiTextArea.setRows(4);
        this.asciiTextArea.setColumns(this.byteInLine * 10);
        this.asciiTextArea.setEnabled(false);
        asciiPanel.add(this.asciiTextArea);

        jPanel.add(binaryPanel, BorderLayout.WEST);
        jPanel.add(hexPanel, BorderLayout.EAST);
        jPanel.add(asciiPanel, BorderLayout.SOUTH);

        frame.add(jPanel, BorderLayout.CENTER);
    }

    private boolean isEndFile(){
        return this.bytes == null || this.bytes.length == 0 || this.pageNum * this.pageSize >= this.bytes.length;
    }
    private boolean isStartFile(){
        return this.bytes == null || this.bytes.length == 0 || this.pageNum == 0;
    }
    private void initBtnPanel(JFrame frame){
        JPanel jPanel = new JPanel();
        jPanel.setSize(this.width, this.btnPanelHeight);

        JButton prevBtn = new JButton("上一页");
        prevBtn.setName("prevPage");
        prevBtn.addActionListener(e -> {
            if(isStartFile()){
                return;
            }
            prevBtn.setEnabled(false);
            this.pageNum--;
            showBinaryInfo();
            prevBtn.setEnabled(true);
        });
        JButton nextBtn = new JButton("下一页");
        nextBtn.setName("nextPage");
        nextBtn.addActionListener(e -> {
            if(isEndFile()){
                return;
            }
            nextBtn.setEnabled(false);
            this.pageNum++;
            showBinaryInfo();
            nextBtn.setEnabled(true);
        });

        jPanel.add(prevBtn);
        jPanel.add(nextBtn);

        frame.add(jPanel, BorderLayout.SOUTH);
    }

    /**
     * 自定义的窗口事件
     */
    private static class CustomWindowListener implements WindowListener {

        private final FileByteShow main;//父类对象

        private CustomWindowListener(FileByteShow main){
            this.main = main;
        }

        @Override
        public void windowOpened(WindowEvent e) {

        }

        @Override
        public void windowClosing(WindowEvent e) {
            this.main.fileChooseFrame.setVisible(false);
            this.main.fileChooseFrame.dispose();
        }

        @Override
        public void windowClosed(WindowEvent e) {

        }

        @Override
        public void windowIconified(WindowEvent e) {

        }

        @Override
        public void windowDeiconified(WindowEvent e) {

        }

        @Override
        public void windowActivated(WindowEvent e) {

        }

        @Override
        public void windowDeactivated(WindowEvent e) {

        }
    }
}
