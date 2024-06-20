import java.io.*;
import java.net.*;

public class MP3Server {
    private static final int PORT = 12345;
    private static final String MUSIC_DIR = "music"; // Укажите путь к каталогу с MP3-файлами

    public static void main(String[] args) throws IOException {
        ServerSocket serverSocket = new ServerSocket(PORT);
        System.out.println("Server started on port " + PORT);

        while (true) {
            Socket clientSocket = serverSocket.accept();
            new Thread(new ClientHandler(clientSocket)).start();
        }
    }

    private static class ClientHandler implements Runnable {
        private Socket socket;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try (
                    DataInputStream in = new DataInputStream(socket.getInputStream());
                    DataOutputStream out = new DataOutputStream(socket.getOutputStream())
            ) {
                String command = in.readUTF();
                if (command.equals("LIST")) {
                    File dir = new File(MUSIC_DIR);
                    File[] files = dir.listFiles((d, name) -> name.endsWith(".mp3"));
                    assert files != null;
                    out.writeInt(files.length);
                    for (File file : files) {
                        out.writeUTF(file.getName());
                    }
                } else if (command.startsWith("GET ")) {
                    String fileName = command.substring(4);
                    File file = new File(MUSIC_DIR, fileName);
                    if (file.exists() && file.isFile()) {
                        out.writeLong(file.length());
                        try (FileInputStream fis = new FileInputStream(file)) {
                            byte[] buffer = new byte[4096];
                            int bytesRead;
                            while ((bytesRead = fis.read(buffer)) != -1) {
                                out.write(buffer, 0, bytesRead);
                            }
                        }
                    } else {
                        out.writeLong(0);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
