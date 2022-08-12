import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.io.PrintWriter;
import java.util.HashSet;

/**
 * This class handles the socket (user connections)
 * The server accepts multiple connections with unique usernames
 * Commands are simply : #login, #status, #dm, #logoff
 * when a user logs in it announces that the user has logged on to all active
 * users and it's added to collection of users
 * When a user logs off it announces that the user has disconnected to all
 * active users and the user is deleted from the collection of users
 * The user is able to check who else is currently logged on
 * The user is able to create and join rooms and send messages directly to that
 * group once the user has joined
 * the user is able to send Direct Messages to other active users
 */
public class ServerUser implements Runnable {

    private final Socket userSocket;
    public final Server server;
    private String login = null;
    private OutputStream outputStream;
    private PrintWriter out;
    private final List<ServerUser> userList;
    private final Lock chatLock;
    private final Condition chatReady;
    private HashSet<String> rooms = new HashSet<>();

    public ServerUser(Server server, Socket userSocket) {

        this.userList = server.getUserList();
        this.userSocket = userSocket;
        this.server = server;
        chatLock = new ReentrantLock();
        chatReady = chatLock.newCondition();
    }

    @Override
    public void run() {
        try {
            beUserSocket();
        } catch (IOException | InterruptedException e) {
        }
    }

    private void beUserSocket() throws IOException, InterruptedException {

        InputStream inputStream = userSocket.getInputStream();
        outputStream = userSocket.getOutputStream();

        out = new PrintWriter(outputStream);

        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        String line;

        // welcome message for when the user first connects to the server
        // the server asks the user to select a username
        // if the username is taken it will let the user know and ask to try again
        String welcomeMsg1 = "\n========== WELCOME TO THE ULTIMATE CHAT ROOM ==========\n";
        String welcomeMsg2 = "PLEASE CHOOSE A USERNAME: #login <username> (4 - 10 characters)";

        out.println(welcomeMsg1);
        out.println(welcomeMsg2);
        out.flush();

        while ((line = reader.readLine()) != null) {
            String[] tokens = line.split(" "); // here I am splitting the user input to make the commands
            if (tokens != null && tokens.length > 0) {
                String cmd = tokens[0]; // the first token is the COMMAND

                // The user can log off and is disconnected from the server
                if ("#logoff".equalsIgnoreCase(cmd)) {
                    doLogoff();
                    break;
                }
                /*
                 * the user will select an username
                 * if the Main Server's List is of size 1 or more it means there are other
                 * logged on users
                 * if there are already users logged on the program creates another List and
                 * copies the Main Server's List.
                 * The program then checks using the new List to see if the username is already
                 * taken
                 * The program also checks if the user is already logged on; if so it will give
                 * an error if the user tries to log in again
                 */
                else if ("#login".equalsIgnoreCase(cmd)) {
                    if (login == null) {
                        chatLock.lock();
                        try {
                            List<ServerUser> users = new ArrayList<>();

                            if (userList.size() >= 1) {
                                boolean nameIsTaken = false;
                                for (int i = 0; i < userList.size(); i++) {
                                    ServerUser user = userList.get(i);
                                    users.add(user);
                                }
                                for (ServerUser userName : users) {
                                    if (tokens[1].equalsIgnoreCase(userName.getLogin())) {
                                        // outputStream.write("\nUSERNAME TAKEN, PLEASE TRY AGAIN\n\n".getBytes());
                                        out.println("\nUSERNAME TAKEN, PLEASE TRY AGAIN\n");
                                        out.flush();
                                        nameIsTaken = true;
                                        break;
                                    }
                                }
                                if (!nameIsTaken) {
                                    doLogin(tokens);
                                }
                            } else {
                                doLogin(tokens);
                            }
                            chatReady.signalAll();
                        } finally {
                            chatLock.unlock();
                        }
                    } else {
                        out.println("\nYOU ARE ALREADY SIGNED IN\n");
                        out.flush();
                    }
                }
                /*
                 * Here are the commands available once the user is logged on
                 * Check status: it checks the currently active users
                 * The user is able to send Direct Messages to an active user
                 * the user is able to create/join a room and send messages to that room
                 * the user is able to leave the room
                 * The user is able to send messages to all active users in the "main lobby"
                 */
                else if (login != null) {

                    if ("#dm".equalsIgnoreCase(cmd)) {
                        sendDM(tokens);
                    } else if ("#status".equalsIgnoreCase(cmd)) {
                        showUsers(out);
                    } else if ("#join".equalsIgnoreCase(cmd)) {
                        doJoinRoom(tokens);
                    } else if (cmd.length() > 0 && cmd.charAt(0) == '@') {
                        sendMsgToRoom(tokens);
                    } else if ("#leave".equalsIgnoreCase(cmd)) {
                        doLeave(tokens);
                    } else {
                        if (line.length() > 0) {
                            String msg = login + ": " + line;
                            sendMsg(msg);
                        }
                    }
                } else {
                    out.println("\nPLEASE LOGIN FIRST\n");
                    out.flush();
                }
            }
        }
        userSocket.close();
    }

    public String getLogin() {
        return login;
    }

    // broadcast messages to all active users in the main lobby
    public void sendMsg(String msg) throws IOException {
        for (ServerUser worker : userList) {
            worker.send(msg);
        }
    }

    public void showUsers(PrintWriter out) throws IOException {

        // outputStream.write("\nUSERS CURRENTLY ONLINE: \n".getBytes());
        out.println("\nUSERS CURRENTLY ONLINE: ");
        for (ServerUser worker : userList) {
            // outputStream.write((worker.getLogin() + "\n").getBytes());
            out.println(worker.getLogin());
        }
        out.flush();
    }

    //
    private void doLogin(String[] tokens) throws IOException {

        if (tokens.length == 2) {
            String login = tokens[1];

            if ((login.length() >= 4) && (login.length() <= 10)) {
                String msg1 = "LOGIN SUCCESSFUL!!! YOU CAN NOW PARTICIPATE!";
                String msg2 = "TO DISCONNECT: #logoff";
                String msg3 = "TO CHECK ONLINE USERS: #status";
                String msg4 = "TO SEND DIRECT MESSAGES: #dm <username> <msg>";
                String msg5 = "TO JOIN/CREATE ROOM: #join <roomName>";
                out.println();
                out.println(msg1);
                out.println(msg2);
                out.println(msg3);
                out.println(msg4);
                out.println(msg5);
                out.println();
                out.flush();
                chatLock.lock();
                try {
                    userList.add(this);
                    chatReady.signalAll();
                } finally {
                    chatLock.unlock();
                }

                this.login = login;
                System.out.println("User logged in: " + login);

                // Sending to all active users a message when the current user logs on
                // the message is NOT shown for the current user
                String onlineMsg = "\n" + login + " HAS COME ONLINE\n";
                for (ServerUser worker : userList) {

                    if (!login.equals(worker.getLogin())) {
                        worker.send(onlineMsg);
                    }
                }
            } else {
                String msg = "ERROR LOGIN\n";
                // outputStream.write(msg.getBytes());
                out.println(msg);
                out.flush();
            }
        }
    }

    // this method is just to display messages
    public void send(String msg) throws IOException {

        out.println(msg);
        out.flush();
    }

    private void doLogoff() throws IOException {

        chatLock.lock();
        try {
            server.removeUser(this);

            List<ServerUser> onlineUsers = userList;
            // send other online users current user's status
            String onlineMsg = "\n" + login + " HAS DISCONNECTED\n";
            System.out.println("User logged off: " + login);

            // shows message to all active users once the current user disconnects
            for (ServerUser user : onlineUsers) {

                if (!login.equals(user.getLogin())) {
                    user.send(onlineMsg);
                }
            }
            chatReady.signalAll();
        } finally {
            chatLock.unlock();
        }
        userSocket.close();
    }

    private void sendDM(String[] tokens) throws IOException {

        if (tokens.length >= 3) {
            String sendTo = tokens[1];
            String msg = "";
            boolean userFound = false;

            // since the input are broken down to tokens
            // the program concatenates beyond the 3rd token (index 2 and beyond)
            for (int i = 2; i < tokens.length; i++) {
                msg += tokens[i] + " ";
            }

            chatLock.lock();
            try {
                List<ServerUser> onlineUsers = userList;

                int endOfList = 0;
                for (ServerUser user : onlineUsers) {
                    endOfList++;
                    if (sendTo.equalsIgnoreCase(user.getLogin())) {
                        userFound = true;
                        if (userFound) {
                            String completeMsg = "DM from " + login + ": " + msg;
                            user.send(completeMsg);
                            out.println("DM sent to " + sendTo + ": " + msg);
                            out.flush();
                        }
                    } else {
                        // if the DM recipient is not found the sender receives an error
                        if (!userFound && endOfList == onlineUsers.size()) {
                            out.println("\nUSER NOT FOUND\n");
                            out.flush();
                        }
                    }
                }
                chatReady.signalAll();
            } finally {
                chatLock.unlock();
            }
        }
    }

    // boolean to check if the Room exists
    public boolean hasJoinedRoom(String chatRoom) {
        return rooms.contains(chatRoom.toLowerCase());
    }

    private void doJoinRoom(String[] tokens) throws IOException {
        chatLock.lock();
        try {
            if (tokens.length > 1) {
                String chatRoom = tokens[1];
                if (chatRoom.length() >= 3 && chatRoom.length() <= 6) {
                    rooms.add(chatRoom.toLowerCase()); // changing the name of the room to lowercase to avoid case
                                                       // sensitivity
                    out.println("\nYOU HAVE JOINED " + chatRoom.toUpperCase());
                    out.flush();
                    String msg = "TO SEND MESSAGES TO ROOM: @" + chatRoom.toUpperCase();
                    String msg2 = "TO LEAVE ROOM: #leave " + chatRoom.toUpperCase() + "\n";
                    send(msg);
                    send(msg2);
                } else {
                    String msg = "\nCHAT ROOM NAME MUST BE 3-6 CHARACTERS LONG! TRY AGAIN\n";
                    send(msg);
                }
                chatReady.signalAll();
            } else {
                String msg = "\nERROR! PLEASE ENTER CHAT ROOM NAME\n";
                send(msg);
            }
        } finally {
            chatLock.unlock();
        }
    }

    private void doLeave(String[] tokens) throws IOException {
        chatLock.lock();
        try {
            if ((tokens.length > 1) && (hasJoinedRoom(tokens[1]))) {
                String chatRoom = tokens[1];
                rooms.remove(chatRoom.toLowerCase());
                String msg = "\nYOU HAVE LEFT " + chatRoom.toUpperCase() + "\n";
                send(msg);
            } else {
                String msg = "\nYOU ARE NOT IN THIS ROOM\n";
                send(msg);
            }
            chatReady.signalAll();
        } finally {
            chatLock.unlock();
        }
    }

    private void sendMsgToRoom(String[] tokens) throws IOException {
        if (tokens.length >= 2) {
            String chatRoom = tokens[0].substring(1); // the first (0) character is @ it assigns character 1 and beyond
                                                      // as the chatroom
            String msg = "";

            for (int i = 1; i < tokens.length; i++) {
                msg += tokens[i] + " ";
            }

            if (hasJoinedRoom(chatRoom)) {
                chatLock.lock();
                try {
                    List<ServerUser> users = userList;
                    for (ServerUser user : users) {

                        if (user.hasJoinedRoom(chatRoom)) {
                            String sendMsg = login + " @" + chatRoom + ": " + msg;
                            user.send(sendMsg);
                        }
                    }
                    chatReady.signalAll();
                } finally {
                    chatLock.unlock();
                }
            } else {
                String errorMsg = "\nYOU HAVEN'T JOINED " + chatRoom.toUpperCase() + "\n";
                send(errorMsg);
            }
        } else {
            String msg = "ERROR SENDING MESSAGE!\n";
            send(msg);
        }
    }
}
