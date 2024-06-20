import javazoom.jl.decoder.JavaLayerException;
import javazoom.jl.player.advanced.AdvancedPlayer;
import javazoom.jl.player.advanced.PlaybackEvent;
import javazoom.jl.player.advanced.PlaybackListener;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class MP3Client extends JFrame {
    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 12345;

    private JList<String> fileList;
    private DefaultListModel<String> listModel;
    private JButton playButton;
    private JButton stopButton;
    private JButton nextButton;
    private JButton resetButton;
    private JSlider progressBar;
    private AdvancedPlayer player;
    private Thread playerThread;
    private volatile boolean playing;
    private volatile boolean stopped;
    private int currentFileIndex = 0;

    public MP3Client() {
        super("MP3 Player");

        listModel = new DefaultListModel<>();
        fileList = new JList<>(listModel);
        playButton = new JButton("Play");
        stopButton = new JButton("Stop");
        nextButton = new JButton("Next");
        resetButton = new JButton("Reset");
        progressBar = new JSlider(0, 100, 0);
        progressBar.setValue(0);

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(new JScrollPane(fileList), BorderLayout.CENTER);
        JPanel controlPanel = new JPanel();
        controlPanel.add(playButton);
        controlPanel.add(stopButton);
        controlPanel.add(nextButton);
        controlPanel.add(resetButton);
        panel.add(controlPanel, BorderLayout.SOUTH);
        panel.add(progressBar, BorderLayout.NORTH);

        add(panel);
        setSize(400, 300);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setVisible(true);

        playButton.addActionListener(this::play);
        stopButton.addActionListener(this::stop);
        nextButton.addActionListener(this::next);
        resetButton.addActionListener(this::reset);

        loadFileList();

        // Thread to update progress bar
        new Thread(() -> {
            while (true) {
                if (playing) {
                    progressBar.setValue(progressBar.getValue() + 1);
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private void loadFileList() {
        try (Socket socket = new Socket(SERVER_HOST, SERVER_PORT);
             DataInputStream in = new DataInputStream(socket.getInputStream());
             DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {

            out.writeUTF("LIST");
            int fileCount = in.readInt();
            List<String> files = new ArrayList<>();
            for (int i = 0; i < fileCount; i++) {
                files.add(in.readUTF());
            }
            listModel.clear();
            files.forEach(listModel::addElement);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void play(ActionEvent e) {
        String selectedFile = fileList.getSelectedValue();
        if (selectedFile != null) {
            currentFileIndex = fileList.getSelectedIndex();
            playFile(selectedFile);
        }
    }

    private void playFile(String fileName) {
        try (Socket socket = new Socket(SERVER_HOST, SERVER_PORT);
             DataInputStream in = new DataInputStream(socket.getInputStream());
             DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {

            out.writeUTF("GET " + fileName);
            long fileSize = in.readLong();
            if (fileSize > 0) {
                byte[] buffer = new byte[4096];
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                int bytesRead;
                while (fileSize > 0 && (bytesRead = in.read(buffer, 0, (int) Math.min(buffer.length, fileSize))) != -1) {
                    byteArrayOutputStream.write(buffer, 0, bytesRead);
                    fileSize -= bytesRead;
                }
                InputStream inputStream = new ByteArrayInputStream(byteArrayOutputStream.toByteArray());
                if (playerThread != null && playerThread.isAlive()) {
                    player.close();
                }
                player = new AdvancedPlayer(inputStream);
                playerThread = new Thread(() -> {
                    try {
                        playing = true;
                        stopped = false;
                        progressBar.setValue(0);
                        player.setPlayBackListener(new PlaybackListener() {
                            @Override
                            public void playbackFinished(PlaybackEvent evt) {Ja
                                playing = false;
                                if (!stopped && currentFileIndex < listModel.getSize() - 1) {
                                    SwingUtilities.invokeLater(() -> next(null));
                                }
                            }
                        });
                        player.play();
                    } catch (JavaLayerException ex) {
                        ex.printStackTrace();
                    }
                });
                playerThread.start();
            }
        } catch (IOException | JavaLayerException ex) {
            ex.printStackTrace();
        }
    }

    private void stop(ActionEvent e) {
        if (player != null) {
            stopped = true;
            player.close();
            playing = false;
            progressBar.setValue(0);
        }
    }

    private void next(ActionEvent e) {
        if (currentFileIndex < listModel.getSize() - 1) {
            currentFileIndex++;
            fileList.setSelectedIndex(currentFileIndex);
            playFile(fileList.getSelectedValue());
        }
    }

    private void reset(ActionEvent e) {
        stop(null);
        if (currentFileIndex < listModel.getSize()) {
            playFile(fileList.getSelectedValue());
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(MP3Client::new);
    }
}
