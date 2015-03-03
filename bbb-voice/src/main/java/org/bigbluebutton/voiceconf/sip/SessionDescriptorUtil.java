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
package org.bigbluebutton.voiceconf.sip;

import java.util.Enumeration;

import org.zoolu.sdp.MediaDescriptor;
import org.zoolu.sdp.MediaField;
import org.zoolu.sdp.SessionDescriptor;
import org.zoolu.tools.Parser;

import org.slf4j.Logger;
import org.red5.logging.Red5LoggerFactory;

public class SessionDescriptorUtil {
    public static final String SDP_MEDIA_AUDIO = "audio";
    public static final String SDP_MEDIA_VIDEO = "video";

	public static int getLocalMediaPort(SessionDescriptor localSdp, String mediaName) {
        Logger log = Red5LoggerFactory.getLogger(CallAgent.class, "sip");

        int localMediaPort = 0;
        
        for (Enumeration e = localSdp.getMediaDescriptors().elements(); e.hasMoreElements();) {
            MediaField media = ((MediaDescriptor) e.nextElement()).getMedia();

            if (media.getMedia().equals(mediaName)) {
                localMediaPort = media.getPort();
                log.debug("SessionDescriptorUtil => Found LOCAL media: " + media.getMedia() + " with port: " + localMediaPort);
            }
        }
        
        return localMediaPort;
    }
    
	public static int getRemoteMediaPort(SessionDescriptor remoteSdp, String mediaName) {
        Logger log = Red5LoggerFactory.getLogger(CallAgent.class, "sip");

    	int remoteMediaPort = 0;

        for (Enumeration e = remoteSdp.getMediaDescriptors().elements(); e.hasMoreElements();) {
            MediaDescriptor descriptor = (MediaDescriptor) e.nextElement();
            MediaField media = descriptor.getMedia();

            if (media.getMedia().equals(mediaName)) {
                remoteMediaPort = media.getPort();
                log.debug("SessionDescriptorUtil => Found REMOTE media: " + media.getMedia() + " with port: " + remoteMediaPort);
            }
        }
        
        return remoteMediaPort;
    }
	
	public static String getRemoteMediaAddress(SessionDescriptor remoteSdp) {
		return (new Parser(remoteSdp.getConnection().toString())).skipString().skipString().getString();
	}
}
