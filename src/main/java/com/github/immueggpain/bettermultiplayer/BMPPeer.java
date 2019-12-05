package com.github.immueggpain.bettermultiplayer;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(description = "Start BMP client", name = "client", mixinStandardHelpOptions = true, version = Launcher.VERSTR)
public class BMPPeer implements Callable<Void> {

	@Option(names = { "-p", "--port" }, required = true, description = "server's port")
	public int serverPort;

	@Override
	public Void call() throws Exception {
		// check tap device
		if (!hasTapAdapter()) {
			System.out.println("Please intall tap adapter");
			Process process = new ProcessBuilder("ovpn\\tap-windows.exe").inheritIO().start();
			int exitCode = process.waitFor();
			if (exitCode != 0) {
				System.err.println("install failed! exit code: " + exitCode);
				return null;
			}
			// wait a sec
			Thread.sleep(1000);
		}
		return null;
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
