package com.github.immueggpain.bettermultiplayer;

import java.util.concurrent.Callable;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.HelpCommand;

@Command(description = "Toolkit written in java.", name = "javatool", mixinStandardHelpOptions = true,
		version = Launcher.VERSTR, subcommands = { HelpCommand.class, BMPUDPHub.class, BMPPeer.class })
public class Launcher implements Callable<Void> {

	public static final String VERSTR = "1.2.0";

	public static void main(String[] args) {
		int exitCode = new CommandLine(new Launcher()).setCaseInsensitiveEnumValuesAllowed(true).execute(args);
		System.exit(exitCode);
	}

	@Override
	public Void call() throws Exception {
		CommandLine.usage(this, System.out);
		return null;
	}

}
