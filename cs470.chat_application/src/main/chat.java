/*
 * CS470 - Project 1
 * Developers:
 * Rafik Keshishians
 * Salem Alharbi
 * 
 * 
 * Online source used: Java NIO SocketChannel (non-blocking IO)
 * URL:     http://tutorials.jenkov.com/java-nio/index.html
 * 
 * 
 */
package main;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.*;

public class chat {
	private int myPortNumber = 1111;
	private boolean exit = false;
	private List<Connection> connections = new ArrayList<Connection>();
	private ServerSocketChannel serverSocketChannel;
	private Selector socketSelector;
	private ByteBuffer readBuffer;

	public static void main(String[] args) throws Exception {
		chat chatApp = new chat();
		try {
			// chatApp.setMyPortNumber(Integer.parseInt(args[0]));
			chatApp.serverRunner();
			chatApp.takeInput();
		} catch (Exception e) {
			System.out.println("Please run the program with this format:java chat <port number>");
		}
	}

	public void serverRunner() throws IOException {
		socketSelector = Selector.open();
		serverSocketChannel = ServerSocketChannel.open();
		serverSocketChannel.configureBlocking(false);
		serverSocketChannel.register(socketSelector, SelectionKey.OP_ACCEPT);
		serverSocketChannel.socket().bind(new InetSocketAddress(myPortNumber));

		Thread t = new Thread() {
			public void run() {
				try {
					server();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		};
		t.start();
	}

	public void server() throws Exception {
		// boolean conExists; //no need because client will not send connect
		SelectionKey key = null;
		while (!exit) {
			// conExists = false;
			try {
				// Wait for an event one of the registered channels
				socketSelector.select();

				// Iterate over the set of keys for events
				Iterator<SelectionKey> selectedKeys = socketSelector.selectedKeys().iterator();

				while (selectedKeys.hasNext()) {
					key = (SelectionKey) selectedKeys.next();
					selectedKeys.remove();
					if (!key.isValid()) {
						continue;
					}
					// check the request is a new connection or reading
					// from a connection new connection request
					else if (key.isAcceptable()) {
						this.accept(key);
						// connection already exists, reading message
					} else if (key.isReadable()) {
						this.read(key);
					} else if (key.isConnectable()) {
						System.out.println("is connectable");
					}

				}
			} catch (Exception e) {
				e.getMessage();
				break;
			} /*
				 * finally { if (key != null) { key.channel().close();
				 * key.cancel(); } }
				 */
		}
	}

	// creates a new connection by using the selector key
	private void accept(SelectionKey key) {
		try {
			ServerSocketChannel serverSocketChannel = (ServerSocketChannel) key.channel();
			SocketChannel socketChannel = serverSocketChannel.accept();

			// Socket socket = socketChannel.socket();
			socketChannel.configureBlocking(false);

			// Register SocketChannel in the selector and wait for client
			socketChannel.register(socketSelector, SelectionKey.OP_READ);
			String rip = getRemoteIP(socketChannel);
			System.out.println("New connection from: " + rip);

			Connection con = new Connection(socketChannel, rip, getMyPortNumber(), "server");
			connections.add(con);

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	// reading the message using the key of the socketchannel
	private void read(SelectionKey key) throws IOException {
		readBuffer = ByteBuffer.allocate(9000);
		SocketChannel socketChannel = (SocketChannel) key.channel();
		String remoteIp = getRemoteIP(socketChannel);
		int numRead;
		try {
			readBuffer.clear();
			numRead = socketChannel.read(readBuffer);
			byte[] data = new byte[numRead];

			System.arraycopy(readBuffer.array(), 0, data, 0, numRead);
			String message = new String(data);

			System.out.println("Message received from " + getRemoteIP(socketChannel) + ": " + message);
		} catch (Exception e) {
			System.out.println("Peer " + remoteIp + " terminates the connection");
			key.channel().close();
			key.cancel();
			socketChannel.close();
			for (int i = 0; i < connections.size(); i++)
				if (connections.get(i).getConnectionIp().equals(remoteIp))
					connections.remove(i);
			// e.printStackTrace();
			return;
		}
	}

	public void send(String conId, String msg) throws IOException {
		int id = Integer.parseInt(conId) - 1;
		byte[] message = new String(msg).getBytes();
		ByteBuffer buffer = ByteBuffer.wrap(message);
		if (connections.get(id).getSocketChannel().isConnected()) {
			connections.get(id).getSocketChannel().write(buffer);
		}
		buffer.clear();
	}

	public void connect(String destIp, String dstPrt) throws Exception {
		SocketChannel socketChannel = null;
		InetSocketAddress isa = null;
		int timeout = 5000;
		boolean conExists = false;
		try {
			int destPort = Integer.parseInt(dstPrt);
			socketChannel = SocketChannel.open();
			/*
			 * if (destIp.equals(getMyIp()) ||
			 * destIp.toLowerCase().equals("localhost") ||
			 * destIp.equals("127.0.0.1")) { System.out.println(
			 * "The connection request is from the same computer"); conExists =
			 * true; } else { for (int i = 0; i < connections.size(); i++) { if
			 * (destIp.equals(connections.get(i).getConnectionIp())) {
			 * System.out.println("The connection already exists"); conExists =
			 * true; } } }
			 */
			socketChannel.socket().setSoTimeout(timeout);
			if (!conExists) {
				isa = new InetSocketAddress(destIp, destPort);
				socketChannel.connect(isa);
				socketChannel.configureBlocking(false);

				System.out.println("The connection to peer " + destIp + " is successfully established;");
				Connection con = new Connection(socketChannel, destIp, destPort, "client");
				connections.add(con);
				return;
			}
		} catch (Exception e) {
			System.out.println("connection is not made correctly");
		} /*finally {
			socketChannel.close();
		}*/
	}

	public void list() throws IOException {
		System.out.println("id: IP address               Port No.	Connection Type");
		for (int i = 0; i < connections.size(); i++) {
			if (connections.get(i).getSocketChannel().isConnected() && connections.get(i).getSocketChannel().isOpen())
				System.out.println((i + 1) + " " + connections.get(i).getConnectionIp() + " "
						+ connections.get(i).getDisplayPort() + " " + connections.get(i).getType());
		}
	}

	public void terminate(String conId) {
		try {
			int id = Integer.parseInt(conId) - 1;
			connections.get(id).getSocketChannel().socket().close();
			connections.get(id).getSocketChannel().close();
			connections.remove(id);
		} catch (Exception e) {
			System.out.println("Please enter the ID within the list.");
		}
	}

	public void exit() throws IOException {
		this.exit = true;
		// terminate all the connections
		for (int i = 0; i < connections.size(); i++) {
			terminate("" + i);
		}
		socketSelector.close();
		serverSocketChannel.close();
	}

	public int getMyPortNumber() {
		return this.myPortNumber;
	}

	public void setMyPortNumber(int port) {
		this.myPortNumber = port;
	}

	public String getMyIp() throws UnknownHostException {
		return Inet4Address.getLocalHost().getHostAddress();
	}

	public String getRemoteIP(SocketChannel sc) throws IOException {
		return sc.getRemoteAddress().toString().replace("/", "").split(":")[0];
	}

	public void takeInput() throws Exception {
		Scanner keyboard;
		String input;
		String[] command;
		System.out.println("Enter help for list of commands");
		while (!exit) {
			keyboard = new Scanner(System.in);
			input = keyboard.nextLine();
			input = input.toLowerCase().trim();
			command = input.split("\\s+");
			switch (command[0]) {
			case "help":
				if (command.length > 1)
					System.out.println("Too many arguments");
				else
					help();
				break;
			case "myip":
				if (command.length > 1)
					System.out.println("Too many arguments");
				else
					System.out.println("The IP address is " + getMyIp());
				break;
			case "myport":
				if (command.length > 1)
					System.out.println("Too many arguments");
				else
					System.out.println("The program runs on port number " + getMyPortNumber());
				break;
			case "connect":
				/*
				 * if (command.length == 1) printErrorMsg(
				 * "The destination is not specified"); else if (command.length
				 * == 2) printErrorMsg("The port number is not specified"); else
				 * if (command.length > 3) printErrorMsg("Too many arguments");
				 * else connect(command[1], command[2]);
				 */
				connect(command[1], command[2]);
				// connect("localhost", "1111");

				break;
			case "list":
				if (command.length > 1)
					System.out.println("Too many arguments");
				else
					list();
				break;
			case "terminate":
				if (command.length == 1)
					printErrorMsg("The connection ID is not specified");
				else
					terminate(command[1]);
				break;
			case "send":
				if (command.length == 1)
					printErrorMsg("The connection ID is not specified");
				else if (command.length == 2)
					printErrorMsg("There is no message.");
				else
					for (int i = 3; i < command.length; i++)
						command[2] += " " + command[i];
				send(command[1], command[2]);
				break;
			case "exit":
				if (command.length > 1) {
					printErrorMsg("Too many arguments");
				} else {
					exit();
				}
				break;
			default:
				printErrorMsg("!!!!!");
				break;
			}
		}
	}

	public void help() throws Exception {
		System.out.println(
				"|*******************************************HELP MENU*****************************************|");
		System.out.println(
				"| 1) help                                                                                     |");
		System.out.println("|\t\tDescription: Display the command options and their description.               |");
		System.out.println(
				"|---------------------------------------------------------------------------------------------|");
		System.out.println(
				"| 2) myip                                                                                     |");
		System.out.println("|\t\tDescription: Display the IP address.                                          |");
		System.out.println(
				"|---------------------------------------------------------------------------------------------|");
		System.out.println(
				"| 3) myport                                                                                   |");
		System.out.println("|\t\tDescription: Display listening port.                                          |");
		System.out.println(
				"|---------------------------------------------------------------------------------------------|");
		System.out.println(
				"| 4) connect                                                                                  |");
		System.out.println("|\t\tDescription: Establish connection with <destination IP> using <port number>.  |");
		System.out.println(
				"|---------------------------------------------------------------------------------------------|");
		System.out.println(
				"| 5) list                                                                                     |");
		System.out.println("|\t\tDescription: Display list of connections.                                     |");
		System.out.println(
				"|---------------------------------------------------------------------------------------------|");
		System.out.println(
				"| 6) terminate                                                                                |");
		System.out.println("|\t\tDescription: End connection with IP address of <connection id>.               |");
		System.out.println(
				"|---------------------------------------------------------------------------------------------|");
		System.out.println(
				"| 7) send                                                                                     |");
		System.out.println("|\t\tDescription: Send <message> to IP address of <connection id>.                 |");
		System.out.println(
				"|---------------------------------------------------------------------------------------------|");
		System.out.println(
				"| 8) exit                                                                                     |");
		System.out.println("|\t\tDescription: Exit program.                                                    |");
		System.out.println(
				"|*********************************************************************************************|");
	}

	public void printErrorMsg(String msg) {
		System.out.println(msg);
		System.out.println("Please enter again");
	}
}

class Connection {
	private SocketChannel socketChannel;
	private String connectionIp;
	private int displayPort;
	private String type;

	public Connection(SocketChannel socketChannel, String connectionIp, int displayPort, String type) {
		super();
		this.socketChannel = socketChannel;
		this.connectionIp = connectionIp;
		this.displayPort = displayPort;
		this.type = type;
	}

	public SocketChannel getSocketChannel() {
		return socketChannel;
	}

	public void setSocketChannel(SocketChannel socketChannel) {
		this.socketChannel = socketChannel;
	}

	public String getConnectionIp() {
		return connectionIp;
	}

	public void setConnectionIp(String connectionIp) {
		this.connectionIp = connectionIp;
	}

	public int getDisplayPort() {
		return displayPort;
	}

	public void setDisplayPort(int displayPort) {
		this.displayPort = displayPort;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}
}
