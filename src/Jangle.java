public class Jangle {
    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("ERROR: missing arguments");
            printHelp();
        }
        else if (args[0].equals("help") || args[0].equals("-h") || args[0].equals("--help")) {
            printHelp();
        }
        else if (args[0].equals("server")) {
            if (args.length != 2) {
                System.out.println("ERROR: wrong number of arguments");
                printHelp();
                return;
            }

            int port;
            try {
                port = Integer.parseInt(args[1]);
            }
            catch (NumberFormatException nfe) {
                System.out.println("ERROR: could not resolve " + args[1] + " to a port number");
                return;
            }

            ServerMode.startServer(port);
        }
        else if (args[0].equals("chat")) {
            if (args.length != 4) {
                System.out.println("ERROR: wrong number of arguments");
                printHelp();
                return;
            }

            String host = args[1];
            int port;
            try {
                port = Integer.parseInt(args[2]);
            }
            catch (NumberFormatException nfe) {
                System.out.println("ERROR: could not resolve " + args[2] + " to a port number");
                return;
            }

            String username = args[3];

            UserMode.startChat(host, port, username);
        }
        else {
            System.out.println("ERROR: " + args[0] + " is not a command");
            printHelp();
        }
    }

    // prints help message
    private static void printHelp() {
        System.out.println("Valid commands:");
        System.out.println("\tserver <port number>                    (open a server on <port number>)");
        System.out.println("\tchat <hostname> <port number> <name>    (connect to server at <hostname>, port <port number>, with name <name>)");
        System.out.println("\t-h | --help | help                      (print help message)");
    }
}