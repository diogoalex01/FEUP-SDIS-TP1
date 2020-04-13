# sdis1920-t2g08

## Installation Guide

In order to compile and run the source code, it is required to perform the following steps in the project's directory. Start by accesing 

```
$ cd sdis1920-t2g08
```

You'll be able to either manually run a set of commands, or if you're using Windows, you can simply execute the given batch file.

## Using the Script (Windows Only)

If you will be running the program on a Windows machine, you only need to follow one step. When running the script you must define the number of peers envolved, as well as the protocol version used by all of them. There is also an optional flag.

```
./setup.bat number_of_peers protocol_version [-reset]
```

**-reset** : Resets the storage registries used to keep track of systems state

> E.g.: `./setup.bat 3 1.0`

In the previous example you'll see 5 windows popping up. The first is running the RMI Registry. One per requested peer. The last will be the Test Client, the program you'll need to interact with each one of the peers. After this you're all set and you can procede to the Running section to start an instance of any protocol. 

## Building

Once in the source code directory, compile using javac.

```
$ javac *.java
```

## Running

As we're using the RMI registry to invoke the peers' methods, start it by running

```
$ rmiregistry
```

For each pear you'd like to initiate, open a new terminal/command line and type:

```
Peer <Version> <ServerID> <AccessPoint> <MCAddress> <MCPort> <MDBAddress> <MDBPort> <MDRAddress> <MDRPort>
```

> E.g.: `java Peer 1.0 1 AP1 230.0.0.0 4445 231.0.0.0 4446 232.0.0.0 4447`

### Subprotocols

To backup a file, invoke:

```
$ java TestApp <access_point> BACKUP <file_name> <replication_degree>
```
> E.g.: `java Main AP1 BACKUP testFile.txt 3`


To restore a backed up file, invoke:

```
$ java TestApp <access_point> RESTORE <file_name>
```
> E.g.: `java Main AP1 RESTORE testFile.txt`


 To delete a file from the system, invoke:

```
$ java TestApp <access_point> DELETE <file_name>
```
> E.g.: `java Main AP1 DELETE testFile.txt`


To reclaim disk space, invoke:

```
$ java TestApp <access_point> RECLAIM <disk_space>
```
 > E.g.: `java Main AP1 RECLAIM 100`


Lastly, to display a peer's state invoke:

```
$ java TestApp <access_point> STATE
```
> E.g.: `java Main AP1 STATE`

### Where:
* <access_point> is the selected peers access point;
* <file_name> is the name of the chosen file;
* <replication_degree> is the desired number of peer storing a given chunk of the file specified;
* <disk_space> is the amount of space, specified in KBs, a peer is allowed to use to store received chunks;


## Authors

* **Diogo Silva**, up201706892@fe.up.pt

* **João Luz**, up201703782@fe.up.pt
