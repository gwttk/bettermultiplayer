package com.github.immueggpain.bettermultiplayer;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;

import com.github.immueggpain.bettermultiplayer.Launcher.ServerSettings;

public class BMPUDPHub {

	private static class Player {
		/** time of the last packet received from this player */
		public long t;
		public InetSocketAddress saddr;
	}

	private HashMap<InetSocketAddress, Player> activePlayers = new HashMap<>();
	private DatagramSocket socket;

	public void run(ServerSettings settings) {
		try {
			Thread recvThread = Util.execAsync("recv_thread", () -> recv_thread(settings.server_port));
			Thread removeExpiredPlayerThread = Util.execAsync("remove_expired_player_thread",
					() -> remove_expired_player_thread(settings.server_port));

			recvThread.join();
			removeExpiredPlayerThread.join();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void recv_thread(int listen_port) {
		try {
			// setup sockets
			InetAddress allbind_addr = InetAddress.getByName("0.0.0.0");
			socket = new DatagramSocket(listen_port, allbind_addr);

			// setup packet
			byte[] recvBuf = new byte[4096];
			DatagramPacket p = new DatagramPacket(recvBuf, recvBuf.length);

			// recv loop
			while (true) {
				p.setData(recvBuf);
				socket.receive(p);
				InetSocketAddress saddr = (InetSocketAddress) p.getSocketAddress();
				updatePlayerInfo(saddr, System.currentTimeMillis());
				broadcastPacket(saddr, p);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/** daemon cleaning expired players */
	private void remove_expired_player_thread(int listen_port) {
		while (true) {
			synchronized (activePlayers) {
				long now = System.currentTimeMillis();
				for (Iterator<Entry<InetSocketAddress, Player>> iterator = activePlayers.entrySet().iterator(); iterator
						.hasNext();) {
					Entry<InetSocketAddress, Player> entry = iterator.next();
					long last = entry.getValue().t;
					if (now - last > 60000)
						iterator.remove();
				}
			}
			Util.sleep(5000);
		}
	}

	private void updatePlayerInfo(InetSocketAddress saddr, long t) {
		synchronized (activePlayers) {
			Player player = activePlayers.get(saddr);
			if (player == null) {
				player = new Player();
				player.saddr = saddr;
			}
			player.t = t;
		}
	}

	private void broadcastPacket(InetSocketAddress source, DatagramPacket p) {
		synchronized (activePlayers) {
			for (Entry<InetSocketAddress, Player> entry : activePlayers.entrySet()) {
				InetSocketAddress dest = entry.getValue().saddr;

				if (dest.equals(source))
					continue;

				p.setSocketAddress(dest);
				try {
					socket.send(p);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}

}