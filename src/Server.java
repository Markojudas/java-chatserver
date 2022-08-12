import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

/*
This is the server that accepts connections from different users, creating new Threads for each new user
*/

public class Server implements Runnable {

    private final int serverPort;
    private ArrayList<ServerUser> userList = new ArrayList<>(); // this is the Main List collecting logged on users

    public Server(int serverPort) {
        this.serverPort = serverPort;
    }

    public List<ServerUser> getUserList() {
        return userList;
    }

    @Override
    public void run() {

        try {
            try (ServerSocket serverSocket = new ServerSocket(serverPort)) {
                while (true) {
                    System.out.println("ABOUT TO ACCEPT CLIENT CONNECTION...");
                    Socket userSocket = serverSocket.accept();
                    System.out.println("ACCEPTED CONNECTION FROM " + userSocket);

                    ServerUser user = new ServerUser(this, userSocket);
                    // workerList.add(worker);
                    Thread t = new Thread(user);
                    t.start();
                }
            }
        } catch (IOException e) {
        }

    }

    void removeUser(ServerUser serverUser) {
        userList.remove(serverUser);

    }
}
