package com.github.immueggpain.bettermultiplayer;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

public class Launcher {

	private static final String VERSTR = "0.1.0";

	public static class ClientSettings {
		public String server_ip;
		public int server_port;
		public String password;
		public String tap_ip;
		public String tap_mask;
	}

	public static class ServerSettings {
		public String password;
		public String cert;
		public String private_key;
	}

	public static void main(String[] args) {
		// in laucher, we dont use log file, just print to console
		// cuz it's all about process input args
		try {
			new Launcher().run(args);
		} catch (Exception e) {
			e.printStackTrace();
			System.err.println("use -h to see help");
		}
	}

	private void run(String[] args) throws ParseException {
		// option long names
		String help = "help";
		String mode = "mode";
		String password = "password";
		String server_ip = "server_ip";
		String server_port = "server_port";
		String cert = "cert";
		String private_key = "private_key";
		String tap_ip = "tap_ip";
		String tap_mask = "tap_mask";

		// define options
		Options options = new Options();
		options.addOption("h", help, false, "print help then exit");
		options.addOption(Option.builder("m").longOpt(mode).hasArg().desc("mode, server or client, default is client")
				.argName("MODE").build());
		options.addOption(Option.builder("w").longOpt(password).hasArg()
				.desc("password of server or client, must be same, recommend 64 bytes").argName("PASSWORD").build());
		options.addOption(Option.builder("s").longOpt(server_ip).hasArg().desc("server ip").argName("IP").build());
		options.addOption(
				Option.builder("p").longOpt(server_port).hasArg().desc("server port").argName("PORT").build());
		options.addOption(Option.builder("c").longOpt(cert).hasArg()
				.desc("SSL cert chain file path. default is fullchain.pem").argName("PATH").build());
		options.addOption(Option.builder("k").longOpt(private_key).hasArg()
				.desc("SSL private key file path. default is privkey.pem").argName("PATH").build());
		options.addOption(
				Option.builder("i").longOpt(tap_ip).hasArg().desc("IP of your new virtual LAN").argName("IP").build());
		options.addOption(Option.builder("a").longOpt(tap_mask).hasArg().desc("IP mask of your new virtual LAN")
				.argName("IP MASK").build());

		// parse from cmd args
		DefaultParser parser = new DefaultParser();
		CommandLine cmd = parser.parse(options, args);

		// first let's check if it's help
		if (cmd.hasOption(help)) {
			String header = "";
			String footer = "\nPlease report issues at https://github.com/Immueggpain/smartproxy/issues";

			HelpFormatter formatter = new HelpFormatter();
			formatter.printHelp("java -jar smartproxy-" + VERSTR + ".jar", header, options, footer, true);
			return;
		}

		// server or client
		if (cmd.hasOption(mode) && cmd.getOptionValue(mode).equals("server")) {
			// run as server
			ServerSettings settings = new ServerSettings();
			settings.password = cmd.getOptionValue(password);
			settings.cert = cmd.getOptionValue(cert, "fullchain.pem");
			settings.private_key = cmd.getOptionValue(private_key, "privkey.pem");
			try {
				new BMPServer().run(settings);
			} catch (Exception e) {
				e.printStackTrace();
			}
			return;
		} else {
			// run as client
			ClientSettings settings = new ClientSettings();
			settings.server_ip = cmd.getOptionValue(server_ip);
			settings.server_port = Integer.parseInt(cmd.getOptionValue(server_port));
			settings.password = cmd.getOptionValue(password);
			settings.tap_ip = cmd.getOptionValue(tap_ip);
			settings.tap_mask = cmd.getOptionValue(tap_mask);
			try {
				new BMPClient().run(settings);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
}
