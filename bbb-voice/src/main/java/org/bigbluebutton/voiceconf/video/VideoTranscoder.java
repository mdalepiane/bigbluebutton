package org.bigbluebutton.voiceconf.video;

import org.bigbluebutton.voiceconf.sip.ProcessMonitor;

public class VideoTranscoder {
	private boolean isVideoPresent;
	private final String streamName;
	private ProcessMonitor transcoder;

	public VideoTranscoder(String streamName, ProcessMonitor transcoder, boolean videoPresent) {
		this.streamName = streamName;
		this.transcoder = transcoder;
		this.isVideoPresent = videoPresent;
	}

	public String getStreamName() {
		return this.streamName;
	}

	public boolean isVideoPresent() {
		return this.isVideoPresent;
	}

	public void setVideoPresent(boolean videoPresent) {
		this.isVideoPresent = videoPresent;
	}
}
