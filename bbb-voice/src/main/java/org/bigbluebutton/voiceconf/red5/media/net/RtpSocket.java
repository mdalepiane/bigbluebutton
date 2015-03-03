/**
* BigBlueButton open source conferencing system - http://www.bigbluebutton.org/
* 
* Copyright (c) 2012 BigBlueButton Inc. and by respective authors (see below).
*
* This program is free software; you can redistribute it and/or modify it under the
* terms of the GNU Lesser General Public License as published by the Free Software
* Foundation; either version 3.0 of the License, or (at your option) any later
* version.
* 
* BigBlueButton is distributed in the hope that it will be useful, but WITHOUT ANY
* WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
* PARTICULAR PURPOSE. See the GNU Lesser General Public License for more details.
*
* You should have received a copy of the GNU Lesser General Public License along
* with BigBlueButton; if not, see <http://www.gnu.org/licenses/>.
*
*/
package org.bigbluebutton.voiceconf.red5.media.net;

import java.net.InetAddress;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.SocketException;

import org.slf4j.Logger;
import org.red5.logging.Red5LoggerFactory;


/** RtpSocket implements a RTP socket for receiving and sending RTP packets. 
  * <p> RtpSocket is associated to a DatagramSocket that is used
  * to send and/or receive RtpPackets.
  */
public class RtpSocket {
   protected static Logger log = Red5LoggerFactory.getLogger(RtpSocket.class, "sip");

   /** UDP socket */
   DatagramSocket socket;
        
   /** Remote address */
   InetAddress r_addr;

   /** Remote port */
   int r_port;

   private final byte[] payload = new byte[10];

   private final DatagramPacket txDatagram = new DatagramPacket(payload, payload.length);
   private final DatagramPacket rxDatagram = new DatagramPacket(payload, payload.length);

   private static final int SO_TIMEOUT = 5000;
   
   /** Creates a new RTP socket (only receiver) */ 
   public RtpSocket(DatagramSocket datagram_socket) {  
	   socket=datagram_socket;
	   r_addr=null;
	   r_port=0;

      try {
      socket.setSoTimeout(this.SO_TIMEOUT);
      }
      catch(SocketException e) {
         log.debug("[RtpSocket] unable to set socket timeout.");
      }
   }

   /** Creates a new RTP socket (sender and receiver) */ 
   public RtpSocket(DatagramSocket datagram_socket, InetAddress remote_address, int remote_port) {  
	   socket = datagram_socket;
	   r_addr = remote_address;
	   r_port = remote_port;
   }

   /** Returns the RTP DatagramSocket */ 
   public DatagramSocket getDatagramSocket() {  
	   return socket;
   }

   
   
   /** Receives a RTP packet from this socket */
   public void receive(RtpPacket rtpp) throws IOException, SocketTimeoutException {  
	   rxDatagram.setData(rtpp.getPacket());
	   socket.receive(rxDatagram);
	   rtpp.setPacketLength(rxDatagram.getLength());     
   }
   
   
   
   /** Sends a RTP packet from this socket */      
   public void send(RtpPacket rtpp) throws IOException {  
	   txDatagram.setData(rtpp.getPacket());
	   txDatagram.setAddress(r_addr);
	   txDatagram.setPort(r_port);
	   if (!socket.isClosed())
		   socket.send(txDatagram);
   }

   /** Closes this socket */      
   public void close() {  
	   //socket.close();
   }
}
