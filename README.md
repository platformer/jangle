# Jangle
This is a terminal chat program written entirely in Java. You can either host a Jangle server, or connect to a preexisting server if you know its IP address. This program is primarily intended to work on MacOS, Linux, and/or other Unix-like operating systems.

# Usage
First download Java 1.8 or newer. Then download the project's jar file [here](https://github.com/platformer/jangle/blob/main/target/jangle-1.0.jar).

To host a server, navigate to the desired working directory and run the following command (as a background job according to the shell you are using):  
`java -jar jangle-1.0.jar server`

To connect to an existing server, run the following command:  
`java -jar jangle-1.0.jar chat <server IP> <desired username>`

## Notes
*   You can try to run a server in Windows, but due to Windows' default firewall settings, users will not be able to connect to your server. It is definitely possible to get it working, but you will have to fiddle with your Windows settings. If you wish to try, look into allowing other PCs to connect to yours via TCP.
*   You can find a version of this project that uses Postgres for its internal database [here](https://github.com/platformer/jangle/tree/dev-postgres).
*   You can find a version of this project that uses Redis for its internal database [here](https://github.com/platformer/jangle/tree/dev-redis).
