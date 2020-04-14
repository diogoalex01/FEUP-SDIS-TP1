sdis1920-t2g08

- Installation Guide

In the development of the project the following Java builds were used:

    > java 11.0.1 2018-10-16 LTS
    > Java(TM) SE Runtime Environment 18.9 (build 11.0.1+13-LTS)

In order to compile and run the source code, it is required to perform the following steps in the project's directory. Start by accessing it. If you're using Windows use the command line.

    > cd sdis1920-t2g08/

Now, you'll be able to either manually run a set of commands, or if you're using Windows, you can simply execute a given batch file.

- Using the Script (Windows Only)

If you will be running the program on a Windows machine, you only need to follow one step. Beware that this method uses default values for the IP addresses and ports of the channel connections and peer access points.

When running the script you must define the number of peers involved, as well as the protocol version used by all of them. There is also an optional flag.  On the command line type:

    > setup.bat number_of_peers protocol_version [-reset]

-reset : Resets the storage registries used to keep track of the system's state.

    E.g.: setup.bat 3 1.0

In the previous example, you'll see 5 windows popping up. The first is running the RMI Registry, three of them are running peers (one per requested peer) and the last will be the Test Client, where you can interact with each one of the peers by invoking the subprotocols.

After this, you're all set and you can proceed to the Subprotocols Section.

- Building

Once in the source code directory, compile using javac.

    > javac *.java

- Running

As we're using the RMI registry to invoke the peers' methods, start it by running:

    > rmiregistry

For each peer you'd like to initiate, open a new terminal and type:

    > Peer <version> <server_ID> <access_point> <MC_Address> <MC_Port> <MDB_Address> <MDB_Port> <MDR_Address> <MDR_Port>
    E.g.: java Peer 1.0 1 AP1 230.0.0.0 4445 231.0.0.0 4446 232.0.0.0 4447

- Subprotocols

To backup a file, invoke:

    > java TestApp <access_point> BACKUP <file_name> <replication_degree>
    E.g.: java Main AP1 BACKUP testFile.txt 3

To restore a backed up file, invoke:

    > java TestApp <access_point> RESTORE <file_name>
    E.g.: java Main AP1 RESTORE testFile.txt

To delete a file from the system, invoke:

    > java TestApp <access_point> DELETE <file_name>
    E.g.: java Main AP1 DELETE testFile.txt

To reclaim disk space, invoke:

    > java TestApp <access_point> RECLAIM <disk_space>
    E.g.: java Main AP1 RECLAIM 100

Lastly, to display a peer's state invoke:

    > java TestApp <access_point> STATE
    E.g.: java Main AP1 STATE

Where:

<access_point> is the selected peer access point;
<file_name> is the name of the chosen file;
<replication_degree> is the desired number of peer storing a given chunk of the specified file;
<disk_space> is the amount of space, in Kilobytes (1000 Bytes), a peer is allowed to use to store received chunks.

Diogo Silva, up201706892@fe.up.pt
João Luz, up201703782@fe.up.pt