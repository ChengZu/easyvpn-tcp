package com.easyvpn.main;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.ArrayList;

import com.easyvpn.main.Packet.IP4Header;

public class UdpClient implements Runnable {
	private Socket socket;
	private InputStream is;
	private OutputStream os;
	private boolean isClose = true;
	private Thread thread;
	private ArrayList<UdpProxy> udpProxys = new ArrayList<>();
	private byte[] cacheBytes = null;
	private boolean haveCacheBytes = false;
	public static final String ANSI_RESET = "\u001B[0m";
	public static final String ANSI_RED = "\u001B[31m";


	public UdpClient(Socket socket) {
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

	public void close() {
		isClose = true;
		try {
			is.close();
			os.close();
			socket.close();
			for(UdpProxy udpProxy : udpProxys) {
				udpProxy.close();
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	public boolean isClose() {
		return socket.isClosed() || isClose;
	}

	public boolean writeToClient(byte[] packet, int offset, int size) {
		if(isClose()) {
			return false;
		}
		try {
			os.write(packet, offset, size);
			return true;
		} catch (IOException e) {
			// TODO Auto-generated catch block
			close();
			e.printStackTrace();
			return false;
		}
	}

	synchronized public void processRecvPacket(byte[] bytes, int size) throws UnknownHostException {

        if(this.haveCacheBytes) {
            byte[] data = new byte[this.cacheBytes.length + size];
            System.arraycopy(this.cacheBytes, 0, data, 0, this.cacheBytes.length);
            System.arraycopy(bytes, 0, data, this.cacheBytes.length, size);
            bytes = data;

            //System.out.println("#####recv size: " + size + " cache size: "+this.cacheBytes.length);
            
            size = this.cacheBytes.length + size;
            this.cacheBytes = null;
            this.haveCacheBytes = false;

        }
        if (size < UdpProxy.HEADER_SIZE) {
            byte[] data = new byte[size];
            System.arraycopy(bytes, 0, data, 0, size);
            this.cacheBytes = data;
            this.haveCacheBytes = true;
            //System.out.println("bad packet size: "+ size +", CacheBytes");
            return;
        }


        ByteBuffer byteBuffer = ByteBuffer.wrap(bytes, 0, size);
        byteBuffer.limit(size);
        Packet packet = new Packet(byteBuffer);
        
        Packet.IP4Header Ip4Header = packet.getIp4Header();
        Packet.UDPHeader UDPHeader = packet.getUdpHeader();
        if (Ip4Header == null || UDPHeader == null) {
            System.out.println(ANSI_RED + "#####process packet error: bad packet" + ANSI_RESET);
            close();
            return;
        }
        
  		//System.out.println("client send size: "+size+"---------packet: "+packet);


        if(size > packet.getIp4Header().totalLength){
            proxyWrite(bytes, packet.getIp4Header().totalLength, packet);
            int nextDataSize = size - packet.getIp4Header().totalLength;
            byte[] data = new byte[nextDataSize];
            System.arraycopy(bytes, packet.getIp4Header().totalLength, data, 0, nextDataSize);
            processRecvPacket(data, nextDataSize);
        }else if(size == packet.getIp4Header().totalLength){
            proxyWrite(bytes, size, packet);
        }else if(size < packet.getIp4Header().totalLength){
            byte[] data = new byte[size];
            System.arraycopy(bytes, 0, data, 0, size);

            this.cacheBytes = data;
            this.haveCacheBytes = true;
        }

    }
	
	public void proxyWrite(byte[] bytes, int size, Packet packet) {
		clearExpireProxy();
		if (udpProxys.size() > Config.MAX_CONNECT) {
			System.out.println("UdpProxy connect max");
			return;
		}
		
		IP4Header Ip4Header = packet.getIp4Header();
		com.easyvpn.main.Packet.UDPHeader UDPHeader = packet.getUdpHeader();
		InetAddress destIp = Ip4Header.destinationAddress;
		int destPort = UDPHeader.destinationPort;
		InetAddress srcIp = Ip4Header.sourceAddress;
		int srcPort = UDPHeader.sourcePort;
		

		
		int dataSize = size - UdpProxy.HEADER_SIZE;
		int offset = UdpProxy.HEADER_SIZE;
		byte[] data = new byte[dataSize];
		System.arraycopy(bytes, offset, data, 0, dataSize);
		DatagramPacket sendPacket = new DatagramPacket(data, dataSize, destIp, destPort);
		
		int index = -1;
		UdpProxy proxy = null;
		for (int i = 0; i < udpProxys.size(); i++) {
			proxy = udpProxys.get(i);
			if (proxy.srcIp.equals(srcIp) && proxy.srcPort == srcPort && proxy.destIp.equals(destIp)
					&& proxy.destPort == destPort) {
				proxy.writeToServer(sendPacket);
				index = i;
				break;
			}
		}

		if (index == -1) {
			proxy = new UdpProxy(this, packet, srcIp, srcPort, destIp, destPort);
			udpProxys.add(proxy);
			proxy.writeToServer(sendPacket);
			// System.out.println("accept, total udp socket: " + udpProxys.size());
		} else {
			// System.out.println("UdpProxy exist");
		}
		
	}

	
	public void clearExpireProxy() {
		synchronized (udpProxys) {
			for (int i = 0; i < udpProxys.size(); i++) {
				if (udpProxys.get(i).isClose()) {
					udpProxys.remove(i);
					i--;
				}
			}
		}
	}

	public void clearAllProxy() {
		synchronized (udpProxys) {
			for (int i = 0; i < udpProxys.size(); i++) {
				udpProxys.get(i).close();
			}
			udpProxys.clear();
		}
	}
	
	@Override
	public void run() {
		try {
			int size = 0;
			byte[] packet = new byte[Config.MUTE];

			while (size != -1 && !isClose()) {
				size = is.read(packet);
				if (size > 0) {
					processRecvPacket(packet, size);
				}
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			//e.printStackTrace();
		} finally {
			close();
		}
	}

}

