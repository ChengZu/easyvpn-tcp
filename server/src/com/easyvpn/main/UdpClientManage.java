package com.easyvpn.main;


import java.util.ArrayList;

public class UdpClientManage {

	private ArrayList<UdpClient> clientList = new ArrayList<UdpClient>();


	public UdpClientManage() {

	}


	public void addClient(UdpClient client) {
		for (int i = 0; i < clientList.size(); i++) {
			if (clientList.get(i).isClose()) {
				clientList.remove(i);
				i--;
			}
		}
		if (clientList.size() < Config.MAX_CONNECT) {
			clientList.add(client);
		} else {
			client.close();
			System.out.println("UdpProxy connect max");
		}
		
		//System.out.println("UdpClientManage client num: " + clientList.size());
	}
	
	public void closeAllClient() {
		for(UdpClient udplient : clientList) {
			udplient.close();
		}
	}


}


