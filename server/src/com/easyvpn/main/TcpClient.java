package com.easyvpn.main;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

public class TcpClient implements Runnable {
	public UdpClientManage udpClientManage = new UdpClientManage();
	private Socket socket;
	private int ip;
	private int port;
	private InputStream is;
	private OutputStream os;
	private TcpProxy tcpProxy;
	private boolean isClose = true;
	private byte protocol = 0;
	private Thread thread;

	public TcpClient(Socket socket) {
		this.socket = socket;
		try {
			is = socket.getInputStream();
			os = socket.getOutputStream();

			thread = new Thread(this);
			thread.start();
			isClose = false;
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	public void close(boolean closeBrother) {
		isClose = true;
		try {
			if (tcpProxy != null && closeBrother) {
				tcpProxy.close(false);
			}
			is.close();
			os.close();
			socket.close();
			udpClientManage.closeAllClient();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	public boolean isClose() {
		return socket.isClosed() || isClose;
	}

	public void writeToClient(byte[] packet, int offset, int size) {
		try {
			if(!isClose())
				os.write(packet, offset, size);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			close(true);
			e.printStackTrace();
		}
	}
	
	@Override
	public void run() {
		try {
			int readSize = 0;
			byte[] readBytes = new byte[Packet.IP4_HEADER_SIZE];
			while (readSize < Packet.IP4_HEADER_SIZE) {
				int size = is.read(readBytes, readSize, Packet.IP4_HEADER_SIZE - readSize);
				if(size == -1) return;
				readSize += size;
			}

			IPHeader header = new IPHeader(readBytes, 0);
			protocol = header.getProtocol();
			if (protocol == IPHeader.TCP) {
				ip = header.getSourceIP();
				port = header.getDestinationIP();
				tcpProxy = new TcpProxy(this, ip, port);
			} else if (protocol == IPHeader.UDP) {
				UdpClient UdpClient = new UdpClient(this.socket);
				this.udpClientManage.addClient(UdpClient);
				return;
			} else {
				System.out.println("fist packet bad header value");
				return;
			}

			int size = 0;
			byte[] packet = new byte[Config.MUTE];
			while (size != -1 && !isClose()) {
				size = is.read(packet);
				if (size > 0) {
					tcpProxy.writeToServer(packet, 0, size);
				}
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			// e.printStackTrace();
		} finally {
			if (this.protocol != IPHeader.UDP) //此TcpClient保存在Server中, udpClientManage 进行关闭
				close(true);
		}
	}

}
