# Jangle
This is a terminal chat program written entirely in Java. You can either host a Jangle server, or connect to a preexisting server if you know its IP address. This program is primarily intended to work on MacOS, Linux, and/or other Unix-like operating systems.

# Setting up a Jangle Server
1.  Install Java 8 or newer.
2.  Install Redis using the official [guide](https://redis.io/docs/getting-started/).
3.  Now we need to setup a password for you Redis server.  
    1.  Run this command in your terminal: `redis-server --daemonize yes`
    2.  Now run this command: `redis-cli`. You should see a prompt that looks like `127.0.0.1:6379> `.
    3.  Enter the command `CONFIG SET requirepass "<password>"`, where <password> is the password you want to use for Redis.
    4.  Enter the command `AUTH <password>`. It Redis says 'OK', then your password is set. You can quit the prompt with `Ctrl+D`.
4.  When you want to run a Jangle server, first start Redis with `redis-server --daemonize yes`.  
    If you want to shutdown Redis, enter `redis-cli`, then do the following:
    ```
    127.0.0.1:6379> AUTH <password>
    127.0.0.1:6379> shutdown
    ```
9.  You're done setting up Redis! Now, download the jar file for Jangle [here](https://github.com/platformer/jangle/blob/519c54cdcbc45ac6a7e47a78901d06fd348a9c91/target/jangle-1.0.jar). Consult the Usage section for running the jar.

### Notes
*   You can try to replicate the above setup process within Windows, but due to Windows' default firewall settings, users will not be able to connect to your server. It is definitely possible to get it working, but you will have to fiddle with your Windows settings. If you wish to try, look into allowing other PCs to connect to yours via TCP.
*   Jangle assumes that your Redis server is running on the default port of 6379. Your server may not be running on this port if you've changed it or if you somehow have multiple Redis servers running on your system.

# Usage
To host a server, navigate to the desired working directory and run the following command (as a background job according to the shell you are using):  
`java -jar jangle-1.0.jar server <redis password>`

To connect to an existing server, run the following command:  
`java -jar jangle-1.0.jar chat <server IP> <desired username>`
