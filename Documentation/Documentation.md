# Byzantine Project Documentation

# Preface

This document is written in Markdown. To view the rendered version, you can use your tool of choice. Followings are tools that I prefer:
1. [SublimeText](https://www.sublimetext.com/3) with [Markdown​Preview](https://packagecontrol.io/packages/MarkdownPreview) plugin
2. [VS Code](https://code.visualstudio.com/download) (it has MarkdownPreview built-in)

# Implementation

This project's communication protocol implements [Lamport's clock](https://en.wikipedia.org/wiki/Lamport_timestamps) to ensure distributed synchronization. _Some additional details regarding behaviors will be further addressed in the [Test plan](#test-plan) section below._

## Server communication

When a server starts, it create 2 threads and does the following:
1. Trying to open socket to other servers (its peers)
2. Accepting new connection request (either from other servers or from clients)

Upon accepting new open socket, the server create new thread per connection to handle them all concurrently (non-blocking from each other).

For any message the server receives, it then update its local time to ensure the local time is no smaller than the timestamp of the message (per Lamport's logic). After it finishes processing a message, it also increment its local time to advance the clock.

If the server needs to write to the file, it has to talk to other servers to ensure mutually synchronized by doing the followings:
1. Send a Write Acquire Request message to every servers and put the message onto its queue
2. Wait for all Write Acquire Response message from every other servers regarding the request it just sent
3. Proceed to critical section (write to the file)
4. Send a Write Release Request to every servers to free the lock
5. Exit critical section

The logic above is proven by Lamport's paper: _L. Lamport. Time, Clocks and the Ordering of Events in a Distributed System. Communications of the ACM, 21(7):558–565, July 1978._

## Client communication

When a client starts, it tries to create sockets to connect to all servers. Then in a loop, it does the following:
1. Randomly decide if should read or write
2. Randomly decide the file number
3. Run the [algorithm](#hash-function) below to choose which server(s) to send request to
4. Display server's response or error message

Since at any given time the client only needs to do one thing (send either read or write request), it does not need multiple threads to handle any of its workflow. So the execution path is top down.

Before sending any message, the client increment its local time to advance the clock. For any message the client receives, it then update its local time to ensure the local time is no smaller than the timestamp of the message (per Lamport's logic).

## Hash function
Per this requirements:
> There exists a hash function, H, such that for each object, Ok, H(Ok) yields a value in the range 0 - 6

The client implements this hash function by given a (object) number n, its hash value is `H(n) = n mod 7`. This ensures the hash value of the object is always between 0 and 6 to fit the 7 servers.

Per this requirement:
> Compute 3 servers numbered: H(Ok), H(Ok)+1 modulo 7, and H(Ok)+2 modulo 7

The client simply uses the value `H(n)` from above to come up with these hashes per servers respectively: `H(n)`, `(H(n) + 1) mod 7`, and `(H(n) + 2) mod 7`

# Test plan

Since the workflow complexity of this project is relatively high due to the amount of distinct moving parts (7 servers and 5 clients, each supports multiple flows), it is quite hard trying to test everything together. So in this test plan, I will break down the flows by directly modify some flags (reference in this [ClientNode](../Client/src/main/java/ClientNode.java) class) of the code to restrict to only 1 path at a time, prove that part works, then move on to the next. Finally, I will let everything run as of production code would, and show the final outputs from a couple of clients' and servers' consoles.

## Single client reads

For this flow, I want the client to only requests random read from different servers. The files may or may not exist yet. 

First turn on Debug mode by the following setting:
```java
private final boolean IS_DEBUGGING = true;
```

Then limit the flow to read-only and the file randomness:
```java
if (IS_DEBUGGING) {
    needToWrite = false;
    fileNumber = random.nextInt(4);
}
```

Follow [these steps](../README.md#how-to-run) to build and run.

For this test case, I choose _client0_ to connect and read from different servers. I have prepared the following files in the Server1 directory:
1. File1.txt: `Server1 File1 Line1`
2. File3.txt: `Server1 File3 Line1`

I also only spin up odd servers (1, 3, 5) and leave the even ones off so that _client0_ won't be able to connect to them.

_Sample client's console:_
```text
> client0 starts at time: 2020-03-16 at 14:51:38.841 CDT
> client0 sends 'client0|ClientReadRequest|1|File1.txt' to server1 at time: 2020-03-16 at 14:51:38.867 CDT
> client0 receives 'server1|ReadSuccessAck|3|Server1 File1 Line1' from server1 at time: 2020-03-16 at 14:51:38.873 CDT
> client0 sends 'client0|ClientReadRequest|6|File7.txt' to server1 at time: 2020-03-16 at 14:51:39.354 CDT
> client0 receives 'server1|ReadFailureAck|8|File 'File7.txt' does not exist' from server1 at time: 2020-03-16 at 14:51:39.355 CDT
> client0: server1 cannot find file 'File7.txt' at time: 2020-03-16 at 14:51:39.355 CDT
> client0 sends 'client0|ClientReadRequest|11|File5.txt' to server5 at time: 2020-03-16 at 14:51:39.598 CDT
> client0 receives 'server5|ReadFailureAck|13|File 'File5.txt' does not exist' from server5 at time: 2020-03-16 at 14:51:39.601 CDT
> client0: server5 cannot find file 'File5.txt' at time: 2020-03-16 at 14:51:39.601 CDT
> client0: server6 is unreachable to read file 'File4.txt' at time: 2020-03-16 at 14:51:39.816 CDT
> client0 sends 'client0|ClientReadRequest|16|File4.txt' to server5 at time: 2020-03-16 at 14:51:39.816 CDT
> client0 receives 'server5|ReadFailureAck|18|File 'File4.txt' does not exist' from server5 at time: 2020-03-16 at 14:51:39.817 CDT
> client0: server5 cannot find file 'File4.txt' at time: 2020-03-16 at 14:51:39.817 CDT
> client0 sends 'client0|ClientReadRequest|21|File2.txt' to server3 at time: 2020-03-16 at 14:51:40.125 CDT
> client0 receives 'server3|ReadFailureAck|23|File 'File2.txt' does not exist' from server3 at time: 2020-03-16 at 14:51:40.128 CDT
> client0: server3 cannot find file 'File2.txt' at time: 2020-03-16 at 14:51:40.129 CDT
> client0 sends 'client0|ClientReadRequest|26|File5.txt' to server5 at time: 2020-03-16 at 14:51:40.554 CDT
> client0 receives 'server5|ReadFailureAck|28|File 'File5.txt' does not exist' from server5 at time: 2020-03-16 at 14:51:40.555 CDT
> client0: server5 cannot find file 'File5.txt' at time: 2020-03-16 at 14:51:40.555 CDT
> client0 sends 'client0|ClientReadRequest|31|File1.txt' to server1 at time: 2020-03-16 at 14:51:40.965 CDT
> client0 receives 'server1|ReadSuccessAck|33|Server1 File1 Line1' from server1 at time: 2020-03-16 at 14:51:40.967 CDT
> client0: server0 is unreachable to read file 'File7.txt' at time: 2020-03-16 at 14:51:41.142 CDT
> client0 sends 'client0|ClientReadRequest|36|File7.txt' to server1 at time: 2020-03-16 at 14:51:41.143 CDT
> client0 receives 'server1|ReadFailureAck|38|File 'File7.txt' does not exist' from server1 at time: 2020-03-16 at 14:51:41.144 CDT
> client0: server1 cannot find file 'File7.txt' at time: 2020-03-16 at 14:51:41.144 CDT
> client0: server2 is unreachable to read file 'File9.txt' at time: 2020-03-16 at 14:51:41.428 CDT
> client0 sends 'client0|ClientReadRequest|41|File9.txt' to server3 at time: 2020-03-16 at 14:51:41.429 CDT
> client0 receives 'server3|ReadFailureAck|43|File 'File9.txt' does not exist' from server3 at time: 2020-03-16 at 14:51:41.430 CDT
> client0: server3 cannot find file 'File9.txt' at time: 2020-03-16 at 14:51:41.430 CDT
> client0: server0 is unreachable to read file 'File5.txt' at time: 2020-03-16 at 14:51:41.430 CDT
> client0: server6 is unreachable to read file 'File5.txt' at time: 2020-03-16 at 14:51:41.430 CDT
> client0 sends 'client0|ClientReadRequest|46|File5.txt' to server5 at time: 2020-03-16 at 14:51:41.431 CDT
> client0 receives 'server5|ReadFailureAck|48|File 'File5.txt' does not exist' from server5 at time: 2020-03-16 at 14:51:41.432 CDT
> client0: server5 cannot find file 'File5.txt' at time: 2020-03-16 at 14:51:41.432 CDT
> client0 sends 'client0|ClientReadRequest|51|File3.txt' to server5 at time: 2020-03-16 at 14:51:41.891 CDT
> client0 receives 'server5|ReadSuccessAck|53|Server1 File3 Line1' from server5 at time: 2020-03-16 at 14:51:41.896 CDT
> client0 sends 'client0|ClientReadRequest|56|File0.txt' to server1 at time: 2020-03-16 at 14:51:42.238 CDT
> client0 receives 'server1|ReadFailureAck|58|File 'File0.txt' does not exist' from server1 at time: 2020-03-16 at 14:51:42.239 CDT
> client0: server1 cannot find file 'File0.txt' at time: 2020-03-16 at 14:51:42.239 CDT
> client0 sends 'client0|ClientReadRequest|61|File7.txt' to server1 at time: 2020-03-16 at 14:51:42.266 CDT
> client0 receives 'server1|ReadFailureAck|63|File 'File7.txt' does not exist' from server1 at time: 2020-03-16 at 14:51:42.267 CDT
> client0: server1 cannot find file 'File7.txt' at time: 2020-03-16 at 14:51:42.267 CDT
> client0 sends 'client0|ClientReadRequest|66|File6.txt' to server1 at time: 2020-03-16 at 14:51:42.304 CDT
> client0 receives 'server1|ReadFailureAck|68|File 'File6.txt' does not exist' from server1 at time: 2020-03-16 at 14:51:42.305 CDT
> client0: server1 cannot find file 'File6.txt' at time: 2020-03-16 at 14:51:42.305 CDT
> client0: server6 is unreachable to read file 'File6.txt' at time: 2020-03-16 at 14:51:42.472 CDT
> client0: server0 is unreachable to read file 'File6.txt' at time: 2020-03-16 at 14:51:42.472 CDT
> client0 sends 'client0|ClientReadRequest|71|File6.txt' to server1 at time: 2020-03-16 at 14:51:42.473 CDT
> client0 receives 'server1|ReadFailureAck|73|File 'File6.txt' does not exist' from server1 at time: 2020-03-16 at 14:51:42.474 CDT
> client0: server1 cannot find file 'File6.txt' at time: 2020-03-16 at 14:51:42.474 CDT
> client0: server2 is unreachable to read file 'File1.txt' at time: 2020-03-16 at 14:51:42.822 CDT
> client0 sends 'client0|ClientReadRequest|76|File1.txt' to server3 at time: 2020-03-16 at 14:51:42.822 CDT
> client0 receives 'server3|ReadSuccessAck|78|Server1 File1 Line1' from server3 at time: 2020-03-16 at 14:51:42.826 CDT
> client0: server2 is unreachable to read file 'File8.txt' at time: 2020-03-16 at 14:51:43.292 CDT
> client0 sends 'client0|ClientReadRequest|81|File8.txt' to server3 at time: 2020-03-16 at 14:51:43.293 CDT
> client0 receives 'server3|ReadFailureAck|83|File 'File8.txt' does not exist' from server3 at time: 2020-03-16 at 14:51:43.294 CDT
> client0: server3 cannot find file 'File8.txt' at time: 2020-03-16 at 14:51:43.294 CDT
> client0 sends 'client0|ClientReadRequest|86|File7.txt' to server1 at time: 2020-03-16 at 14:51:43.395 CDT
> client0 receives 'server1|ReadFailureAck|88|File 'File7.txt' does not exist' from server1 at time: 2020-03-16 at 14:51:43.396 CDT
> client0: server1 cannot find file 'File7.txt' at time: 2020-03-16 at 14:51:43.396 CDT
> client0: server4 is unreachable to read file 'File9.txt' at time: 2020-03-16 at 14:51:43.497 CDT
> client0 sends 'client0|ClientReadRequest|91|File9.txt' to server3 at time: 2020-03-16 at 14:51:43.498 CDT
> client0 receives 'server3|ReadFailureAck|93|File 'File9.txt' does not exist' from server3 at time: 2020-03-16 at 14:51:43.499 CDT
> client0: server3 cannot find file 'File9.txt' at time: 2020-03-16 at 14:51:43.499 CDT
> client0: server2 is unreachable to read file 'File2.txt' at time: 2020-03-16 at 14:51:43.673 CDT
> client0: server4 is unreachable to read file 'File2.txt' at time: 2020-03-16 at 14:51:43.673 CDT
> client0 sends 'client0|ClientReadRequest|96|File2.txt' to server3 at time: 2020-03-16 at 14:51:43.673 CDT
> client0 receives 'server3|ReadFailureAck|98|File 'File2.txt' does not exist' from server3 at time: 2020-03-16 at 14:51:43.674 CDT
> client0: server3 cannot find file 'File2.txt' at time: 2020-03-16 at 14:51:43.674 CDT
> client0 gracefully exits at time: 2020-03-16 at 14:51:43.674 CDT
```

When a client randomly select 1 out of 3 possible servers to send read request, if that server is unreachable, then the client proceeds to the next server to issue request until it runs out of all servers (all are unreachable) then it will display an error message. If any of the 3 servers is reachable to handle the read request, the client will display the result of that server - regardless of the response of that server: Success acknowledgement means the file exists and can read (return along with the its content), Failure acknowledgement means the file does not exist to read.

_Sample server's console:_
```text
> server3 starts listening on (localhost:1373)... at time: 2020-03-16 at 14:50:40.910 CDT
> server3 receives new request from Socket[addr=/127.0.0.1,port=64452,localport=1373] at time: 2020-03-16 at 14:51:26.619 CDT
> server3 receives 'client0|ClientReadRequest|21|File2.txt' from client0 at time: 2020-03-16 at 14:51:40.127 CDT
> server3 sends 'server3|ReadFailureAck|23|File 'File2.txt' does not exist' to client0 at time: 2020-03-16 at 14:51:40.128 CDT
> server3 receives 'client0|ClientReadRequest|41|File9.txt' from client0 at time: 2020-03-16 at 14:51:41.429 CDT
> server3 sends 'server3|ReadFailureAck|43|File 'File9.txt' does not exist' to client0 at time: 2020-03-16 at 14:51:41.429 CDT
> server3 receives 'client0|ClientReadRequest|76|File1.txt' from client0 at time: 2020-03-16 at 14:51:42.822 CDT
> server3 sends 'server3|ReadSuccessAck|78|Server1 File1 Line1' to client0 at time: 2020-03-16 at 14:51:42.825 CDT
> server3 receives 'client0|ClientReadRequest|81|File8.txt' from client0 at time: 2020-03-16 at 14:51:43.293 CDT
> server3 sends 'server3|ReadFailureAck|83|File 'File8.txt' does not exist' to client0 at time: 2020-03-16 at 14:51:43.293 CDT
> server3 receives 'client0|ClientReadRequest|91|File9.txt' from client0 at time: 2020-03-16 at 14:51:43.498 CDT
> server3 sends 'server3|ReadFailureAck|93|File 'File9.txt' does not exist' to client0 at time: 2020-03-16 at 14:51:43.498 CDT
> server3 receives 'client0|ClientReadRequest|96|File2.txt' from client0 at time: 2020-03-16 at 14:51:43.673 CDT
> server3 sends 'server3|ReadFailureAck|98|File 'File2.txt' does not exist' to client0 at time: 2020-03-16 at 14:51:43.674 CDT
```

When a server receives a client's read request for a particular file, if the file exists in its file system then the server sends a success acknowledgement that contains the content of the file back to the client; otherwise it sends a failure acknowledgement back.

Note that in this example, a single client reads multiple servers. But spinning up other client instances will not affect the read behavior because the servers do not have to talk to each other when receiving the read request (see protocol above).

## Single client writes

For this flow, I want the client to only requests random write from different servers. The files may or may not exist yet. If the file being requested does not exist, the server will automatically create the file before writing to it.

First turn on Debug mode by the following setting:
```java
private final boolean IS_DEBUGGING = true;
private Logger logger = new Logger(Logger.LogLevel.Debug);
```

Then limit the flow to write-only and the file randomness:
```java
if (IS_DEBUGGING) {
    needToWrite = true;
    fileNumber = random.nextInt(10);
}
```

Follow [these steps](../README.md#how-to-run) to build and run.

For this test case, I choose _client0_ to connect and read from different servers (1, 2, 3) and leave the rest off so that _client0_ won't be able to connect to them.

_Sample client's console:_
```text
> client0 successfully connects to 3 server(s): (server1, server2, server3) at time: 2020-03-16 at 17:14:22.626 CDT
> client0 starts at time: 2020-03-16 at 17:14:22.626 CDT
> client0: Cannot write to 'File6.txt' because of too many (2) unreachable servers (server6, server0) at time: 2020-03-16 at 17:14:22.956 CDT
> client0: Cannot write to 'File4.txt' because of too many (3) unreachable servers (server4, server5, server6) at time: 2020-03-16 at 17:14:23.053 CDT
> client0 sends 'client0|ClientWriteRequest|1|File7.txt|client0 message #2' to server1 at time: 2020-03-16 at 17:14:23.412 CDT
> client0 receives 'server1|WriteSuccessAck|12|' from server1 at time: 2020-03-16 at 17:14:23.424 CDT
> client0 sends 'client0|ClientWriteRequest|15|File7.txt|client0 message #2' to server2 at time: 2020-03-16 at 17:14:23.424 CDT
> client0 receives 'server2|WriteSuccessAck|26|' from server2 at time: 2020-03-16 at 17:14:23.436 CDT
> client0: Cannot write to 'File4.txt' because of too many (3) unreachable servers (server4, server5, server6) at time: 2020-03-16 at 17:14:23.834 CDT
> client0: Cannot write to 'File6.txt' because of too many (2) unreachable servers (server6, server0) at time: 2020-03-16 at 17:14:24.278 CDT
> client0 sends 'client0|ClientWriteRequest|29|File7.txt|client0 message #5' to server1 at time: 2020-03-16 at 17:14:24.672 CDT
> client0 receives 'server1|WriteSuccessAck|40|' from server1 at time: 2020-03-16 at 17:14:24.778 CDT
> client0 sends 'client0|ClientWriteRequest|43|File7.txt|client0 message #5' to server2 at time: 2020-03-16 at 17:14:24.779 CDT
> client0 receives 'server2|WriteSuccessAck|54|' from server2 at time: 2020-03-16 at 17:14:24.884 CDT
> client0: Cannot write to 'File3.txt' because of too many (2) unreachable servers (server4, server5) at time: 2020-03-16 at 17:14:24.979 CDT
> client0 sends 'client0|ClientWriteRequest|57|File0.txt|client0 message #7' to server1 at time: 2020-03-16 at 17:14:25.341 CDT
> client0 receives 'server1|WriteSuccessAck|68|' from server1 at time: 2020-03-16 at 17:14:25.446 CDT
> client0 sends 'client0|ClientWriteRequest|71|File0.txt|client0 message #7' to server2 at time: 2020-03-16 at 17:14:25.446 CDT
> client0 receives 'server2|WriteSuccessAck|82|' from server2 at time: 2020-03-16 at 17:14:25.551 CDT
> client0 sends 'client0|ClientWriteRequest|85|File2.txt|client0 message #8' to server2 at time: 2020-03-16 at 17:14:25.672 CDT
> client0 receives 'server2|WriteSuccessAck|96|' from server2 at time: 2020-03-16 at 17:14:25.776 CDT
> client0 sends 'client0|ClientWriteRequest|99|File2.txt|client0 message #8' to server3 at time: 2020-03-16 at 17:14:25.776 CDT
> client0 receives 'server3|WriteSuccessAck|110|' from server3 at time: 2020-03-16 at 17:14:25.782 CDT
> client0: Cannot write to 'File6.txt' because of too many (2) unreachable servers (server6, server0) at time: 2020-03-16 at 17:14:25.866 CDT
> client0: Cannot write to 'File6.txt' because of too many (2) unreachable servers (server6, server0) at time: 2020-03-16 at 17:14:26.002 CDT
> client0 sends 'client0|ClientWriteRequest|113|File9.txt|client0 message #11' to server2 at time: 2020-03-16 at 17:14:26.443 CDT
> client0 receives 'server2|WriteSuccessAck|124|' from server2 at time: 2020-03-16 at 17:14:26.546 CDT
> client0 sends 'client0|ClientWriteRequest|127|File9.txt|client0 message #11' to server3 at time: 2020-03-16 at 17:14:26.546 CDT
> client0 receives 'server3|WriteSuccessAck|138|' from server3 at time: 2020-03-16 at 17:14:26.651 CDT
> client0: Cannot write to 'File3.txt' because of too many (2) unreachable servers (server4, server5) at time: 2020-03-16 at 17:14:26.961 CDT
> client0: Cannot write to 'File5.txt' because of too many (3) unreachable servers (server5, server6, server0) at time: 2020-03-16 at 17:14:27.026 CDT
> client0 sends 'client0|ClientWriteRequest|141|File8.txt|client0 message #14' to server1 at time: 2020-03-16 at 17:14:27.206 CDT
> client0 receives 'server1|WriteSuccessAck|152|' from server1 at time: 2020-03-16 at 17:14:27.312 CDT
> client0 sends 'client0|ClientWriteRequest|155|File8.txt|client0 message #14' to server2 at time: 2020-03-16 at 17:14:27.313 CDT
> client0 receives 'server2|WriteSuccessAck|166|' from server2 at time: 2020-03-16 at 17:14:27.414 CDT
> client0 sends 'client0|ClientWriteRequest|169|File8.txt|client0 message #14' to server3 at time: 2020-03-16 at 17:14:27.415 CDT
> client0 receives 'server3|WriteSuccessAck|180|' from server3 at time: 2020-03-16 at 17:14:27.518 CDT
> client0 sends 'client0|ClientWriteRequest|183|File2.txt|client0 message #15' to server2 at time: 2020-03-16 at 17:14:27.764 CDT
> client0 receives 'server2|WriteSuccessAck|194|' from server2 at time: 2020-03-16 at 17:14:27.867 CDT
> client0 sends 'client0|ClientWriteRequest|197|File2.txt|client0 message #15' to server3 at time: 2020-03-16 at 17:14:27.867 CDT
> client0 receives 'server3|WriteSuccessAck|208|' from server3 at time: 2020-03-16 at 17:14:27.970 CDT
> client0 sends 'client0|ClientWriteRequest|211|File8.txt|client0 message #16' to server1 at time: 2020-03-16 at 17:14:28.349 CDT
> client0 receives 'server1|WriteSuccessAck|222|' from server1 at time: 2020-03-16 at 17:14:28.451 CDT
> client0 sends 'client0|ClientWriteRequest|225|File8.txt|client0 message #16' to server2 at time: 2020-03-16 at 17:14:28.452 CDT
> client0 receives 'server2|WriteSuccessAck|236|' from server2 at time: 2020-03-16 at 17:14:28.554 CDT
> client0 sends 'client0|ClientWriteRequest|239|File8.txt|client0 message #16' to server3 at time: 2020-03-16 at 17:14:28.554 CDT
> client0 receives 'server3|WriteSuccessAck|250|' from server3 at time: 2020-03-16 at 17:14:28.656 CDT
> client0: Cannot write to 'File5.txt' because of too many (3) unreachable servers (server5, server6, server0) at time: 2020-03-16 at 17:14:28.940 CDT
> client0 sends 'client0|ClientWriteRequest|253|File1.txt|client0 message #18' to server1 at time: 2020-03-16 at 17:14:29.171 CDT
> client0 receives 'server1|WriteSuccessAck|264|' from server1 at time: 2020-03-16 at 17:14:29.275 CDT
> client0 sends 'client0|ClientWriteRequest|267|File1.txt|client0 message #18' to server2 at time: 2020-03-16 at 17:14:29.275 CDT
> client0 receives 'server2|WriteSuccessAck|278|' from server2 at time: 2020-03-16 at 17:14:29.379 CDT
> client0 sends 'client0|ClientWriteRequest|281|File1.txt|client0 message #18' to server3 at time: 2020-03-16 at 17:14:29.379 CDT
> client0 receives 'server3|WriteSuccessAck|292|' from server3 at time: 2020-03-16 at 17:14:29.488 CDT
> client0: Cannot write to 'File6.txt' because of too many (2) unreachable servers (server6, server0) at time: 2020-03-16 at 17:14:29.822 CDT
> client0 gracefully exits at time: 2020-03-16 at 17:14:29.822 CDT
```

When _client0_ tries to write to _File6.txt_, it should connects to _server6_, _server0_, and _server1_ according to the [hash function](#hash-function) implemented above. But since it can only connect to _server1_, the write is not permitted. Same situation applied when _client0_ tries to write to _File4.txt_ (in this case none of the server is available.) 

When _client0_ tries to write to _File7.txt_ ("message #2"), however, it should connects to _server0_, _server1_, and _server2_ according to the [hash function](#hash-function) implemented above. Since it can connect to both _server1_ and _server2_, the write is permitted.

_Sample server's console:_
```text
> server1 receives 'client0|ClientWriteRequest|1|File7.txt|client0 message #2' from client0 at time: 2020-03-16 at 17:14:23.413 CDT
> Adding message 'server1|WriteAcquireRequest|3|File7.txt|client0 message #2' to the queue at time: 2020-03-16 at 17:14:23.413 CDT
> Queue size before add = 0 at time: 2020-03-16 at 17:14:23.413 CDT
> Queue size after add = 1 at time: 2020-03-16 at 17:14:23.413 CDT
> server1 sends 'server1|WriteAcquireRequest|3|File7.txt|client0 message #2' to server3 at time: 2020-03-16 at 17:14:23.414 CDT
> server1 sends 'server1|WriteAcquireRequest|3|File7.txt|client0 message #2' to server2 at time: 2020-03-16 at 17:14:23.414 CDT
> Checking allowance to proceed to critical session for message 'server1|WriteAcquireRequest|3|File7.txt|client0 message #2'... at time: 2020-03-16 at 17:14:23.415 CDT
> Top of queue = server1|WriteAcquireRequest|3|File7.txt|client0 message #2 at time: 2020-03-16 at 17:14:23.415 CDT
> Current message = server1|WriteAcquireRequest|3|File7.txt|client0 message #2 at time: 2020-03-16 at 17:14:23.415 CDT
> server1 receives 'server3|WriteAcquireResponse|5|File7.txt|client0 message #2' from server3 at time: 2020-03-16 at 17:14:23.417 CDT
> server1 receives 'server2|WriteAcquireResponse|5|File7.txt|client0 message #2' from server2 at time: 2020-03-16 at 17:14:23.417 CDT
> Adding message 'server3|WriteAcquireResponse|5|File7.txt|client0 message #2' to the queue at time: 2020-03-16 at 17:14:23.417 CDT
> Queue size before add = 1 at time: 2020-03-16 at 17:14:23.417 CDT
> Queue size after add = 2 at time: 2020-03-16 at 17:14:23.418 CDT
> Adding message 'server2|WriteAcquireResponse|5|File7.txt|client0 message #2' to the queue at time: 2020-03-16 at 17:14:23.418 CDT
> Queue size before add = 2 at time: 2020-03-16 at 17:14:23.418 CDT
> Queue size after add = 3 at time: 2020-03-16 at 17:14:23.418 CDT
> All senders after request = server3, server2 at time: 2020-03-16 at 17:14:23.418 CDT
> Going into critical session... at time: 2020-03-16 at 17:14:23.418 CDT
> server1 appends 'client0 message #2' to file 'File7.txt' at time: 2020-03-16 at 17:14:23.419 CDT
> server1 sends 'server1|WriteSyncRequest|9|File7.txt|client0 message #2' to server3 at time: 2020-03-16 at 17:14:23.420 CDT
> server1 sends 'server1|WriteSyncRequest|9|File7.txt|client0 message #2' to server2 at time: 2020-03-16 at 17:14:23.421 CDT
> Removing messages off the queue at time: 2020-03-16 at 17:14:23.421 CDT
> Queue size before remove = 3 at time: 2020-03-16 at 17:14:23.421 CDT
> Removing 'server1|WriteAcquireRequest|3|File7.txt|client0 message #2' from the queue at time: 2020-03-16 at 17:14:23.423 CDT
> Removing 'server3|WriteAcquireResponse|5|File7.txt|client0 message #2' from the queue at time: 2020-03-16 at 17:14:23.423 CDT
> Removing 'server2|WriteAcquireResponse|5|File7.txt|client0 message #2' from the queue at time: 2020-03-16 at 17:14:23.423 CDT
> Queue size after remove = 0 at time: 2020-03-16 at 17:14:23.423 CDT
> server1 sends 'server1|WriteReleaseRequest|10|' to server3 at time: 2020-03-16 at 17:14:23.424 CDT
> server1 sends 'server1|WriteReleaseRequest|10|' to server2 at time: 2020-03-16 at 17:14:23.424 CDT
> Going out of critical session access at time: 2020-03-16 at 17:14:23.424 CDT
> server1 sends 'server1|WriteSuccessAck|12|' to client0 at time: 2020-03-16 at 17:14:23.424 CDT
> ...
> server1 receives 'server3|WriteAcquireRequest|283|File1.txt|client0 message #18' from server3 at time: 2020-03-16 at 17:14:29.385 CDT
> Adding message 'server3|WriteAcquireRequest|283|File1.txt|client0 message #18' to the queue at time: 2020-03-16 at 17:14:29.385 CDT
> Queue size before add = 0 at time: 2020-03-16 at 17:14:29.385 CDT
> Queue size after add = 1 at time: 2020-03-16 at 17:14:29.385 CDT
> server1 sends 'server1|WriteAcquireResponse|285|File1.txt|client0 message #18' to server3 at time: 2020-03-16 at 17:14:29.385 CDT
> server1 receives 'server3|WriteSyncRequest|289|File1.txt|client0 message #18' from server3 at time: 2020-03-16 at 17:14:29.487 CDT
> server1 already appended 'client0 message #18' to file 'File1.txt'. Skipping... at time: 2020-03-16 at 17:14:29.487 CDT
> server1 receives 'server3|WriteReleaseRequest|290|' from server3 at time: 2020-03-16 at 17:14:29.487 CDT
> Removing messages off the queue at time: 2020-03-16 at 17:14:29.488 CDT
> Queue size before remove = 1 at time: 2020-03-16 at 17:14:29.488 CDT
> Removing 'server3|WriteAcquireRequest|283|File1.txt|client0 message #18' from the queue at time: 2020-03-16 at 17:14:29.488 CDT
> Queue size after remove = 0 at time: 2020-03-16 at 17:14:29.488 CDT
> ...
```

The above snippet from the server shows a lot of behind the scene information since it is running in debug mode. Here are some explanations.

On the first part:
1. _server1_ receives ClientWriteRequest to write "message #2" to _File7.txt_ from _client0_
2. _server1_ adds the message onto its queue and broadcasts the WriteAcquireRequest to all servers
3. _server1_ receives WriteAcquireResponse from all servers and proceed critical section
4. _server1_ writes "message #2" to _File7.txt_ and broadcasts WriteSyncRequest so that all other servers can do the same
5. _server1_ finishes with its critical section and broadcasts WriteReleaseRequest so that other servers can proceed with theirs respective critical sections

On the second part:
1. _server1_ receives WriteAcquireRequest from _server3_
2. _server1_ adds the message onto its queue
3. _server1_ replies with WriteAcquireResponse message to allow _server3_ from proceeding
4. _server1_ receives WriteSyncRequest from _server3_ to write "client0 message #18" to _File1.txt_. Note that this is a relay message since the _client0_ requests _server3_ to write, and _server3_ asks other servers to run the synchronization protocol
5. Since _server1_ already wrote the same message earlier (_client0_ sent the same request to _server1_), it detects this is a duplicated request and does not write the same message again

Here is a screenshot of the generated files from the servers. Note that each file is replicated exactly the same among all connected servers, and none yields duplicated rows.

![Single client writes](./Img/SingleClientWritesOutput.PNG)

## Multiple clients write

Workflow for multiple clients requesting write concurrently are essentially the same compare to the previous flow. The server uses the same idea to logically ordered messages and ensure all copies of the files have the same order.

I still uses the same set up as the previous test, except that this time I enable 3 clients: _client0_, _client1_, and _client2_.

![Multiple client writes](./Img/MultipleClientWritesOutput.PNG)

# Production

To enable full production workflow (multiple clients connect to multiple servers simultaneously with read and write requests), chang the followings.

[ClientNode.java](../Client/src/main/java/ClientNode.java):
```java
private final boolean IS_DEBUGGING = false;
private Logger logger = new Logger(Logger.LogLevel.Release);
```

[ServerNode.java](../Server/src/main/java/ServerNode.java):
```java
private Logger logger = new Logger(Logger.LogLevel.Release);
```

Follow [these steps](../README.md#how-to-run) to build and run.

Servers' outputs are the same as before so I will not show here. Clients' outputs are a little different since now they have both read and write commands. Here is a sample:
```text
> client0 starts at time: 2020-03-16 at 23:11:35.400 CDT
> client0 sends 'client0|ClientReadRequest|1|File2.txt' to server3 at time: 2020-03-16 at 23:11:35.731 CDT
> client0 receives 'server3|ReadSuccessAck|3|client4 message #10' from server3 at time: 2020-03-16 at 23:11:35.736 CDT
> client0 sends 'client0|ClientReadRequest|6|File8.txt' to server3 at time: 2020-03-16 at 23:11:36.130 CDT
> client0 receives 'server3|ReadSuccessAck|8|client2 message #13{newLine}client0 message #6' from server3 at time: 2020-03-16 at 23:11:36.132 CDT
> client0 sends 'client0|ClientWriteRequest|11|File7.txt|client0 message #2' to server1 at time: 2020-03-16 at 23:11:36.308 CDT
> client0 receives 'server1|WriteSuccessAck|22|' from server1 at time: 2020-03-16 at 23:11:36.318 CDT
> client0 sends 'client0|ClientWriteRequest|25|File7.txt|client0 message #2' to server2 at time: 2020-03-16 at 23:11:36.318 CDT
> client0 receives 'server2|WriteSuccessAck|36|' from server2 at time: 2020-03-16 at 23:11:36.326 CDT
> client0 cannot reach any server (server4, server5, server6) to  reach file 'File11.txt' at time: 2020-03-16 at 23:11:36.781 CDT
> client0 sends 'client0|ClientReadRequest|39|File10.txt' to server3 at time: 2020-03-16 at 23:11:37.016 CDT
> client0 receives 'server3|ReadSuccessAck|41|client3 message #5' from server3 at time: 2020-03-16 at 23:11:37.018 CDT
> client0 sends 'client0|ClientWriteRequest|44|File1.txt|client0 message #5' to server1 at time: 2020-03-16 at 23:11:37.079 CDT
> client0 receives 'server1|WriteSuccessAck|55|' from server1 at time: 2020-03-16 at 23:11:37.185 CDT
> client0 sends 'client0|ClientWriteRequest|58|File1.txt|client0 message #5' to server2 at time: 2020-03-16 at 23:11:37.185 CDT
> client0 receives 'server2|WriteSuccessAck|69|' from server2 at time: 2020-03-16 at 23:11:37.288 CDT
> client0 sends 'client0|ClientWriteRequest|72|File1.txt|client0 message #5' to server3 at time: 2020-03-16 at 23:11:37.289 CDT
> client0 receives 'server3|WriteSuccessAck|83|' from server3 at time: 2020-03-16 at 23:11:37.295 CDT
> client0 sends 'client0|ClientReadRequest|86|File10.txt' to server3 at time: 2020-03-16 at 23:11:37.744 CDT
> client0 receives 'server3|ReadSuccessAck|88|client3 message #5' from server3 at time: 2020-03-16 at 23:11:37.745 CDT
> client0: Cannot write to 'File12.txt' because of too many (3) unreachable servers (server5, server6, server0) at time: 2020-03-16 at 23:11:38.226 CDT
> client0: Cannot write to 'File19.txt' because of too many (3) unreachable servers (server5, server6, server0) at time: 2020-03-16 at 23:11:38.529 CDT
> client0 cannot reach any server (server6, server5, server4) to  reach file 'File11.txt' at time: 2020-03-16 at 23:11:38.582 CDT
> client0 sends 'client0|ClientReadRequest|91|File7.txt' to server2 at time: 2020-03-16 at 23:11:38.792 CDT
> client0 receives 'server2|ReadSuccessAck|93|client0 message #19{newLine}client0 message #2' from server2 at time: 2020-03-16 at 23:11:38.794 CDT
> client0 sends 'client0|ClientReadRequest|96|File2.txt' to server2 at time: 2020-03-16 at 23:11:39.030 CDT
> client0 receives 'server2|ReadSuccessAck|98|client4 message #10' from server2 at time: 2020-03-16 at 23:11:39.032 CDT
> client0 sends 'client0|ClientReadRequest|101|File7.txt' to server1 at time: 2020-03-16 at 23:11:39.474 CDT
> client0 receives 'server1|ReadSuccessAck|103|client0 message #19{newLine}client0 message #2' from server1 at time: 2020-03-16 at 23:11:39.476 CDT
> client0 sends 'client0|ClientReadRequest|106|File3.txt' to server3 at time: 2020-03-16 at 23:11:39.970 CDT
> client0 receives 'server3|ReadSuccessAck|108|client2 message #7{newLine}client1 message #18{newLine}client3 message #16' from server3 at time: 2020-03-16 at 23:11:39.971 CDT
> client0 cannot reach any server (server5, server4, server6) to  reach file 'File18.txt' at time: 2020-03-16 at 23:11:40.352 CDT
> client0: Cannot write to 'File13.txt' because of too many (2) unreachable servers (server6, server0) at time: 2020-03-16 at 23:11:40.462 CDT
> client0 cannot reach any server (server4, server6, server5) to  reach file 'File11.txt' at time: 2020-03-16 at 23:11:40.862 CDT
> client0: Cannot write to 'File17.txt' because of too many (2) unreachable servers (server4, server5) at time: 2020-03-16 at 23:11:41.318 CDT
> client0 sends 'client0|ClientReadRequest|111|File9.txt' to server2 at time: 2020-03-16 at 23:11:41.608 CDT
> client0 receives 'server2|ReadSuccessAck|113|client1 message #1{newLine}client3 message #11{newLine}client2 message #19{newLine}client3 message #17' from server2 at time: 2020-03-16 at 23:11:41.610 CDT
> client0 sends 'client0|ClientWriteRequest|116|File14.txt|client0 message #19' to server1 at time: 2020-03-16 at 23:11:41.781 CDT
> client0 receives 'server1|WriteSuccessAck|127|' from server1 at time: 2020-03-16 at 23:11:41.884 CDT
> client0 sends 'client0|ClientWriteRequest|130|File14.txt|client0 message #19' to server2 at time: 2020-03-16 at 23:11:41.884 CDT
> client0 receives 'server2|WriteSuccessAck|141|' from server2 at time: 2020-03-16 at 23:11:41.986 CDT
> client0 gracefully exits at time: 2020-03-16 at 23:11:41.986 CDT
```