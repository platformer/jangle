# Jangle
This is a terminal chat program written entirely in Java. You can either host a Jangle server, or connect to a preexisting server if you know its IP address.

# Usage
First, you must have Java 8 or higher installed. Then, download the jar file [here](https://github.com/platformer/jangle/blob/main/target/jangle-1.0.jar).

To host a server, run the following command (as a background job according to the shell you are using):  
`java -jar jangle-1.0.jar server`

To connect to an existing server, run the following command:  
`java -jar jangle-1.0.jar chat <server IP> <desired username>`
