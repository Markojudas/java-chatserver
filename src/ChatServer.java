public class ChatServer {

    public static void main(String[] args) {

        int port = 8888;
        Server server = new Server(port);
        Thread t = new Thread(server);
        t.start();
    }

}
