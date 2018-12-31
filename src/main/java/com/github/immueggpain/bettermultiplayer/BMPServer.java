package com.github.immueggpain.bettermultiplayer;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.Key;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

import com.github.immueggpain.bettermultiplayer.Launcher.ServerSettings;

public class BMPServer {

	public void run(ServerSettings settings) {
		try {
			// convert password to aes key
			byte[] bytes = settings.password.getBytes(StandardCharsets.UTF_8);
			byte[] byteKey = new byte[16];
			System.arraycopy(bytes, 0, byteKey, 0, Math.min(byteKey.length, bytes.length));
			SecretKeySpec secretKey = new SecretKeySpec(byteKey, "AES");
			// we use 2 ciphers because we want to support encrypt/decrypt full-duplex
			String transformation = "AES/GCM/PKCS5Padding";
			Cipher encrypter = Cipher.getInstance(transformation);
			Cipher decrypter = Cipher.getInstance(transformation);

			// setup sockets
			InetAddress allbind_addr = InetAddress.getByName("0.0.0.0");
			InetAddress loopback_addr = InetAddress.getByName("127.0.0.1");
			DatagramSocket sclient_s = new DatagramSocket(settings.server_port, allbind_addr);
			DatagramSocket covpn_s = new DatagramSocket(0, loopback_addr);
			int local_ovpn_port = settings.local_ovpn_port;

			// start working threads
			TunnelContext contxt = new TunnelContext();
			Thread transfer_c2s_thread = scmt.execAsync("transfer_c2s", () -> transfer_c2s(sclient_s, decrypter,
					secretKey, loopback_addr, local_ovpn_port, covpn_s, contxt));
			Thread transfer_s2c_thread = scmt.execAsync("transfer_s2c",
					() -> transfer_s2c(covpn_s, encrypter, secretKey, sclient_s, contxt));

			transfer_c2s_thread.join();
			transfer_s2c_thread.join();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private static void transfer_c2s(DatagramSocket sclient_s, Cipher decrypter, Key secretKey,
			InetAddress loopback_addr, int local_ovpn_port, DatagramSocket covpn_s, TunnelContext contxt) {
		try {
			byte[] recvBuf = new byte[4096];
			DatagramPacket p = new DatagramPacket(recvBuf, recvBuf.length);
			while (true) {
				p.setData(recvBuf);
				sclient_s.receive(p);
				contxt.client_addr = p.getAddress();
				contxt.client_port = p.getPort();
				byte[] decrypted = BMPClient.decrypt(decrypter, secretKey, p.getData(), p.getOffset(), p.getLength());
				p.setData(decrypted);
				p.setAddress(loopback_addr);
				p.setPort(local_ovpn_port);
				covpn_s.send(p);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private static void transfer_s2c(DatagramSocket covpn_s, Cipher encrypter, Key secretKey, DatagramSocket sclient_s,
			TunnelContext contxt) {
		try {
			byte[] recvBuf = new byte[4096];
			DatagramPacket p = new DatagramPacket(recvBuf, recvBuf.length);
			while (true) {
				p.setData(recvBuf);
				covpn_s.receive(p);
				if (contxt.client_addr == null)
					continue;
				byte[] encrypted = BMPClient.encrypt(encrypter, secretKey, p.getData(), p.getOffset(), p.getLength());
				p.setData(encrypted);
				p.setAddress(contxt.client_addr);
				p.setPort(contxt.client_port);
				sclient_s.send(p);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private static class TunnelContext {
		public InetAddress client_addr;
		public int client_port;
	}

}
