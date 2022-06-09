package jangle;

public class App
{
    final static int DEFAULT_PORT_NUMBER = 52042;

    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("ERROR: missing arguments");
            printHelp();
        }
        else if (args[0].equals("help") || args[0].equals("-h") || args[0].equals("--help")) {
            printHelp();
        }
        else if (args[0].equals("server")) {
            if (args.length != 1) {
                System.err.println("ERROR: wrong number of arguments");
                printHelp();
                return;
            }

            ServerMode.startServer(DEFAULT_PORT_NUMBER);
        }
        else if (args[0].equals("chat")) {
            if (args.length != 3) {
                System.err.println("ERROR: wrong number of arguments");
                printHelp();
                return;
            }

            String host = args[1];
            String username = args[2];

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
        System.out.println("server                        (start a server)");
        System.out.println("chat <hostname> <name>        (connect to server at <hostname> with username <name>)");
        System.out.println("-h | --help | help            (print help message)");
    }
}
