# Jangle
This is a terminal chat program written entirely in Java. You can either host a Jangle server, or connect to a preexisting server if you know its IP address. This program is primarily intended to work on MacOS, Linux, and/or other Unix-like operating systems.

# Setting up a Jangle Server
1.  Install Java 8 or newer.
2.  Install Postgres 9.5 or newer using the official [guide](https://www.postgresql.org/download/). This step varies greatly depending on your operating system. For MacOS users, I'd recommend installing via Homebrew.
3.  Try running the command `psql` in your terminal. If you get something like this error:

    ```
    psql: error: connection to server on socket "/var/run/postgresql/.s.PGSQL.5432" failed: No such file or directory
            Is the server running locally and accepting connections on that socket?
    ```
    that means your installation did not automatically start the Postgres server, and you'll need to start it yourself. Again, this step will vary greatly depending on your operating system and how you installed Postgres. Look up online how to start a Postgres server on your OS/installation.

    If `psql` doesn't throw an error, quit it with `Ctrl+D`.
4.  Sign into the `postgres` database with the command `psql -d postgres`.
    
    1.  For Linux users, your installation likely created a new system user that is also named `postgres` to manage the Postgres server. Switch to this user with the command `sudo -i -u postgres`, then run the above command.  
    
    You should now see a prompt similar to the following:
    
    ```
    psql (14.3 (Ubuntu 14.3-1.pgdg20.04+1))
    Type "help" for help.
    
    postgres=#
    ```
5.  Create a Postgres user called `jangle_app` by typing this in the prompt:
    ```
    postgres=# CREATE ROLE jangle_app CREATEDB LOGIN PASSWORD '<secure password>';
    ```
    When running Jangle, you will give your `<secure password>` to it so it can use Postgres as the `jangle_app` user.
6.  Create a database named `jangle_app` by typing this in the prompt:
    ```
    postgres=# CREATE DATABASE jangle_app WITH OWNER jangle_app;
    ```
7.  Logout of `psql` with `Ctrl+D` (and if you're logged into the `postgres` user, you can logout of that also).
8.  Try running the command `psql -U jangle_app -d jangle_app`. If you get something like this error:
    ```
    psql: error: connection to server on socket "/var/run/postgresql/.s.PGSQL.5432" failed: FATAL:  Peer authentication failed for user "jangle_app"
    ```
    that means you must enable password authentication for the `jangle_app` user. Otherwise, if you can sign into the database, skip the rest of this step.
    
    Locate the config file called `pg_hba.conf`. The file is probably under `/etc/postgresql/<version>/main/`, but it may differ depending on your installation. In that file, locate the line:
    ```
    # TYPE  DATABASE        USER            ADDRESS                 METHOD
    ```
    Immediately under this line, add:
    ```
    local   jangle_app      jangle_app                              md5
    ```
    You must now restart the Postgres server. You can look up this part, but it's probably the same thing you did to start the server, just replace `start` with `restart` in the command.
    
    You should now be able to run the command at the beginning of this step and login to the database. Logout with `Ctrl+D`.

9.  You're done setting up Postgres! Now, download the jar file for Jangle [here](https://github.com/platformer/jangle/blob/main/target/jangle-1.0.jar). Consult the Usage section for running the jar.

### Notes
*   You can try to replicate the above setup process within Windows, but due to Windows' default firewall settings, users will not be able to connect to your server. The steps for setting up Postgress will also likely be somewhat different. It is definitely possible to get it working, but you will have to fiddle with your Windows settings. If you wish to try, look into allowing other PCs to connect to yours via TCP.
*   Jangle assumes that your Postgres server is running on the default port of 5432. Your server may not be running on this port if you've changed it or if you somehow have multiple Postgres servers running on your system.

# Usage
To host a server, navigate to the desired working directory and run the following command (as a background job according to the shell you are using):  
`java -jar jangle-1.0.jar server <jangle_app password>`

To connect to an existing server, run the following command:  
`java -jar jangle-1.0.jar chat <server IP> <desired username>`
