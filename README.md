## QuickStart for Client
* Make sure you have **[Java](https://jdk.java.net/11/) 8+** installed
* [Download latest build](https://github.com/Immueggpain/bettermultiplayer/releases). Unzip it
* Run `java -jar bettermultiplayer-x.x.x.jar --help` to get help.
* Run client `java -jar .\bettermultiplayer-x.x.x.jar -m client -i <virtual_ip> -a <virtual_mask> -s <server_ip> -p <server_port> -w <password>`.
* Enjoy!

## QuickStart for Server
* Make sure you have **Java 8+** installed
* [Download latest build](https://github.com/Immueggpain/bettermultiplayer/releases). Unzip it
* Run `java -jar bettermultiplayer-x.x.x.jar --help` to get help.
* Setup interfaces
  ```
  openvpn --mktun --dev tap0
  openvpn --mktun --dev tap1
  ip link set dev tap0 up
  ip link set dev tap1 up
  brctl addbr br0
  brctl addif br0 tap0 tap1
  ip link set dev br0 up
  ```
* Run openvpn
  ```
  openvpn --dev tap0 --local 127.0.0.1 --lport <ovpn_port1>
  openvpn --dev tap1 --local 127.0.0.1 --lport <ovpn_port2>
  ```
* Run server
  ```
  java -jar bettermultiplayer-0.1.0.jar -m server -o <ovpn_port1> -p <server_port1> -w <password>
  java -jar bettermultiplayer-0.1.0.jar -m server -o <ovpn_port2> -p <server_port2> -w <password>
  ```
* Enjoy!
