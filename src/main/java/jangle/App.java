package jangle;

public class App
{
    public static final int MAX_CHARS_PER_MESSAGE = 256;
    public static final double SECONDS_BETWEEN_CHUNK_REQUESTS = 1.5;
    public static final int NUM_MESSAGES_PER_CHUNK = 50;
    public static final int MAX_DISPLAY_MESSAGES = 100;
    public static final int POPUP_TIMOUT_MILLIS = 1500;
    public static final int USER_KEEPALIVE_MINUTES = 60;

    private final static int DEFAULT_PORT_NUMBER = 52042;

    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("ERROR: missing arguments");
            printHelp();
        }
        else if (args[0].equals("help") || args[0].equals("-h") || args[0].equals("--help")) {
            printHelp();
        }
        else if (args[0].equals("server")) {
            if (args.length != 2) {
                System.err.println("ERROR: wrong number of arguments");
                printHelp();
                return;
            }

            ServerMode.startServer(args[1], DEFAULT_PORT_NUMBER);
        }
        else if (args[0].equals("chat")) {
            if (args.length != 3) {
                System.err.println("ERROR: wrong number of arguments");
                printHelp();
                return;
            }

            String host = args[1];
            String username = args[2];

            if (username.length() > 64){
                System.err.println("ERROR: username is too long");
                return;
            }

            if (username.contains("\\")){
                System.err.println("ERROR: username contains backslash");
                return;
            }

            username = username.trim().replaceAll("\\s", " ").replaceAll("'", "''");
            UserMode.startChat(host, DEFAULT_PORT_NUMBER, username);
        }
        else {
            System.err.println("ERROR: " + args[0] + " is not a command");
            printHelp();
        }
    }

    // prints help message
    private static void printHelp() {
        System.out.println("Valid Commands:");
        System.out.println("server <jangle_app password>  (start a server)");
        System.out.println("chat <hostname> <name>        (connect to server at <hostname> with username <name>)");
        System.out.println("-h | --help | help            (print help message)");
    }
}
