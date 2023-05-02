package com.easyvpn.main;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;


public class UdpProxy implements Runnable {
	private DatagramSocket datagramSocket;
	private Packet packet;
	private boolean isClose = false;
	public InetAddress ip;
	public int port;
	public InetAddress srcIp;
	public int srcPort;
	public InetAddress destIp;
	public int destPort;
	public static final int HEADER_SIZE = Packet.IP4_HEADER_SIZE + Packet.UDP_HEADER_SIZE;
	public UdpClient udpClient;
	public long lastRefreshTime;

	public UdpProxy(UdpClient udpClient, Packet packet, InetAddress srcIp, int srcPort, InetAddress destIp,
			int destPort) {

		try {

			datagramSocket = new DatagramSocket();
			packet.swapSourceAndDestination();
			this.udpClient = udpClient;
			this.packet = packet;
			this.srcIp = srcIp;
			this.srcPort = srcPort;
			this.destIp = destIp;
			this.destPort = destPort;

			Thread thread = new Thread(this);
			thread.start();

		} catch (SocketException e) {
			e.printStackTrace();
		}
	}
	

	public void writeToServer(DatagramPacket sendPacket) {
		this.lastRefreshTime = System.currentTimeMillis();
		try {
			if(!isClose()) {
				datagramSocket.send(sendPacket);
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			close();
			e.printStackTrace();
		}
	}

	public void close() {
		isClose = true;
		if (datagramSocket != null) {
			datagramSocket.close();
		}
	}

	public boolean isClose() {
		long time = System.currentTimeMillis() - this.lastRefreshTime;
		if (time > 10000) {
			close();
			return true;
		}
		return datagramSocket.isClosed() || isClose;
	}
	

	@Override
	public void run() {
		// TODO Auto-generated method stub
		while (!isClose()) {
			byte[] receBuf = new byte[Config.MUTE - HEADER_SIZE];
			DatagramPacket recePacket = new DatagramPacket(receBuf, receBuf.length);
			try {
				datagramSocket.receive(recePacket);
				
				int len = recePacket.getLength();
				if (len > 0) {
					byte[] dataCopy = new byte[HEADER_SIZE + len];
					
					System.arraycopy(recePacket.getData(), 0, dataCopy, HEADER_SIZE, len);

					ByteBuffer buf = ByteBuffer.wrap(dataCopy);

					Packet newPacket = packet.duplicated();
					//note udp checksum = 0;
					newPacket.updateUDPBuffer(buf, len);
					
					//System.out.println("send to client data: "+newPacket);

					boolean writeDone = udpClient.writeToClient(dataCopy, 0, dataCopy.length);
					if(!writeDone) close();
					
				}
				lastRefreshTime = System.currentTimeMillis();
				
			} catch (IOException e) {
				// TODO Auto-generated catch block
				close();
				// e.printStackTrace();
			}
		}
	}


}
