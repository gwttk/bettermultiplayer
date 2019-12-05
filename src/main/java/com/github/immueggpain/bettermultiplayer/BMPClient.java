/*******************************************************************************
 * MIT License
 *
 * Copyright (c) 2018 Immueggpain
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *******************************************************************************/
package com.github.immueggpain.bettermultiplayer;

import java.io.IOException;
import java.io.InputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.Charset;
import java.security.Key;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.io.IOUtils;

public class BMPClient {

	public void run() {
		// check if tap interface is up

		// send a check udp packet to server
		// server respond, so make sure server is running & aes is correct

		// create sovpn udp socket & cserver udp socket
		// 1 thread recv sovpn, send with cserver to server
		// 1 thread recv cserver, send with sovpn to ovpn
		// start ovpn process
		try {
			// check tap device
			if (!hasTapAdapter()) {
				System.out.println("Please intall tap adapter");
				Process process = new ProcessBuilder("ovpn\\tap-windows.exe").inheritIO().start();
				int exitCode = process.waitFor();
				if (exitCode != 0) {
					System.err.println("install failed! exit code: " + exitCode);
					return;
				}
				// wait a sec
				Thread.sleep(1000);
			}

			// convert password to aes key
			byte[] byteKey = new byte[16];
			SecretKeySpec secretKey = new SecretKeySpec(byteKey, "AES");
			// we use 2 ciphers because we want to support encrypt/decrypt full-duplex
			String transformation = "AES/GCM/PKCS5Padding";
			Cipher encrypter = Cipher.getInstance(transformation);
			Cipher decrypter = Cipher.getInstance(transformation);

			// setup sockets
			InetAddress loopback_addr = InetAddress.getByName("127.0.0.1");
			int local_ovpn_port = 1194;
			int local_listen_port = 1195;
			DatagramSocket sovpn_s = new DatagramSocket(local_listen_port, loopback_addr);
			DatagramSocket cserver_s = new DatagramSocket();

			// start working threads
			Thread transfer_s2c_thread = Util.execAsync("transfer_s2c",
					() -> transfer_s2c(cserver_s, decrypter, secretKey, loopback_addr, local_ovpn_port, sovpn_s));

			// start ovpn
			System.out.println("press ctrl+c again to exit!");

			transfer_s2c_thread.join();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private static void transfer_c2s(DatagramSocket sovpn_s, Cipher encrypter, Key secretKey, InetAddress server_addr,
			int server_port, DatagramSocket cserver_s) {
		try {
			byte[] recvBuf = new byte[4096];
			DatagramPacket p = new DatagramPacket(recvBuf, recvBuf.length);
			while (true) {
				p.setData(recvBuf);
				sovpn_s.receive(p);
				byte[] encrypted = Util.encrypt(encrypter, secretKey, p.getData(), p.getOffset(), p.getLength());
				p.setData(encrypted);
				p.setAddress(server_addr);
				p.setPort(server_port);
				cserver_s.send(p);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private static void transfer_s2c(DatagramSocket cserver_s, Cipher decrypter, Key secretKey,
			InetAddress loopback_addr, int local_ovpn_port, DatagramSocket sovpn_s) {
		try {
			byte[] recvBuf = new byte[4096];
			DatagramPacket p = new DatagramPacket(recvBuf, recvBuf.length);
			while (true) {
				p.setData(recvBuf);
				cserver_s.receive(p);
				byte[] decrypted = Util.decrypt(decrypter, secretKey, p.getData(), p.getOffset(), p.getLength());
				p.setData(decrypted);
				p.setAddress(loopback_addr);
				p.setPort(local_ovpn_port);
				sovpn_s.send(p);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private static void startOvpnProcess(int local_listen_port, String tap_ip, String tap_mask)
			throws IOException, InterruptedException {
		Process process = new ProcessBuilder("ovpn\\openvpn.exe", "--dev", "tap", "--remote", "127.0.0.1",
				String.valueOf(local_listen_port), "udp", "--ifconfig", tap_ip, tap_mask).inheritIO().start();
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

}
