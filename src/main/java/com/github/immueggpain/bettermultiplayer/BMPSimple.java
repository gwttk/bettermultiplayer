package com.github.immueggpain.bettermultiplayer;

import java.io.IOException;
import java.io.InputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.io.IOUtils;
import com.sun.jna.platform.win32.Advapi32Util;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Help.Visibility;

@Command(description = "BMP simple version", name = "simple", mixinStandardHelpOptions = true,
		version = Launcher.VERSTR)
public class BMPSimple implements Callable<Void> {

	@Option(names = { "--server" }, required = false, description = "this is a server",
			showDefaultValue = Visibility.NEVER)
	private boolean isServer = false;

	@Option(names = { "--listen" }, required = false, description = "listening port",
			showDefaultValue = Visibility.ALWAYS)
	public int listenPort = 2333;

	@Option(names = { "--svrname" }, required = false, description = "server's name(ip or domain)",
			showDefaultValue = Visibility.NEVER)
	public String serverName;

	@Option(names = { "--svrport" }, required = false, description = "server's port",
			showDefaultValue = Visibility.ALWAYS)
	public int serverPort = 2333;

	@Option(names = { "-w", "--password", "--pswd" }, required = true, description = "password as encryption key")
	public String password;

	/** socket communicating with ovpn */
	private DatagramSocket socketOvpn;
	/** socket communicating with server */
	private DatagramSocket socketServer;
	private Cipher encrypter;
	private Cipher decrypter;
	private SecretKeySpec secretKey;

	@Override
	public Void call() throws Exception {
		System.out.println("version " + Launcher.VERSTR);

		System.out.println("start as sever? " + isServer);

		// check admin privilege
		boolean isAdmin = isWinAdmin();
		System.out.println("is admin? " + isAdmin);

		// check tap device
		if (!hasTapAdapter()) {
			System.err.println("Please intall tap adapter");
			return null;
		}

		// setup ciphers
		System.out.println("password is [" + password + "]");
		byte[] bytes = password.getBytes(StandardCharsets.UTF_8);
		byte[] byteKey = new byte[16];
		System.arraycopy(bytes, 0, byteKey, 0, Math.min(byteKey.length, bytes.length));
		secretKey = new SecretKeySpec(byteKey, "AES");
		// we use 2 ciphers because we want to support encrypt/decrypt full-duplex
		String transformation = "AES/GCM/PKCS5Padding";
		encrypter = Cipher.getInstance(transformation);
		decrypter = Cipher.getInstance(transformation);

		InetAddress serverInetAddr = InetAddress.getByName(serverName);
		Util.execAsync("recv_ovpn_thread", () -> recv_ovpn_thread(Launcher.LOCAL_PORT, serverInetAddr, serverPort));
		Util.execAsync("recv_server_thread", () -> recv_server_thread(Launcher.LOCAL_OVPN_PORT));

		// start ovpn
		startOvpnProcess(Launcher.LOCAL_PORT, Launcher.LOCAL_OVPN_PORT);

		// no need to join, if ovpn process exits, we exit too.
		// recvOvpnThread.join();
		// recvServerThread.join();

		return null;
	}

	private void recv_ovpn_thread(int listen_port, InetAddress forwardInetAddr, int forwardPort) {
		try {
			// setup sockets
			InetAddress loopback_addr = InetAddress.getByName("127.0.0.1");
			socketOvpn = new DatagramSocket(listen_port, loopback_addr);

			// setup packet
			byte[] recvBuf = new byte[4096];
			DatagramPacket p = new DatagramPacket(recvBuf, recvBuf.length);

			// recv loop
			while (true) {
				p.setData(recvBuf);
				socketOvpn.receive(p);

				byte[] encrypted = Util.encrypt(encrypter, secretKey, p.getData(), p.getOffset(), p.getLength());

				p.setData(encrypted);
				p.setAddress(forwardInetAddr);
				p.setPort(forwardPort);
				socketServer.send(p);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void recv_server_thread(int local_ovpn_port) {
		try {
			InetAddress loopback_addr = InetAddress.getByName("127.0.0.1");

			// setup sockets
			InetAddress allbind_addr = InetAddress.getByName("0.0.0.0");
			socketServer = new DatagramSocket(0, allbind_addr);

			// setup packet
			byte[] recvBuf = new byte[4096];
			DatagramPacket p = new DatagramPacket(recvBuf, recvBuf.length);

			// recv loop
			while (true) {
				p.setData(recvBuf);
				socketServer.receive(p);

				byte[] decrypted;
				try {
					decrypted = Util.decrypt(decrypter, secretKey, p.getData(), p.getOffset(), p.getLength());
				} catch (GeneralSecurityException e) {
					System.err.println(e);
					System.err.println("decrypt failed, skip this packet!");
					continue;
				}

				p.setData(decrypted);
				p.setAddress(loopback_addr);
				p.setPort(local_ovpn_port);
				socketOvpn.send(p);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private static void startOvpnProcess(int local_listen_port, int ovpn_listen_port)
			throws IOException, InterruptedException {
		Process process = new ProcessBuilder("ovpn\\openvpn.exe", "--dev", "tap", "--local", "127.0.0.1", "--lport",
				String.valueOf(ovpn_listen_port), "--remote", "127.0.0.1", String.valueOf(local_listen_port), "--ping",
				"5").inheritIO().start();
		process.waitFor();
	}

	private static boolean hasTapAdapter() throws IOException, InterruptedException {
		Process process = new ProcessBuilder("ovpn\\openvpn.exe", "--show-adapters").redirectErrorStream(true).start();
		InputStream is = process.getInputStream();
		String output = IOUtils.toString(is, Charset.defaultCharset());
		process.waitFor();
		Pattern checkRegex = Pattern.compile("'.+' \\{.+\\}");
		Matcher m = checkRegex.matcher(output);
		return m.find();
	}

	public static boolean isWinAdmin() throws IOException, InterruptedException {
		Advapi32Util.Account[] groups = Advapi32Util.getCurrentUserGroups();
		for (Advapi32Util.Account group : groups) {
			if ("S-1-16-12288".equals(group.sidString)) {
				return true;
			}
		}
		return false;
	}

}
