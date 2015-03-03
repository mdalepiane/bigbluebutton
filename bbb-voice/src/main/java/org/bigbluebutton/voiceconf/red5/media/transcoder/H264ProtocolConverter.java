package org.bigbluebutton.voiceconf.red5.media.transcoder;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.red5.app.sip.codecs.Codec;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.red5.logging.Red5LoggerFactory;

import org.bigbluebutton.voiceconf.red5.media.net.RtpPacket;
import org.bigbluebutton.voiceconf.red5.media.transcoder.VideoProtocolConverter;

public class H264ProtocolConverter extends VideoProtocolConverter {

    private static final Logger log = Red5LoggerFactory.getLogger(H264ProtocolConverter.class, "sip");
    private static final int MAX_RTP_PAYLOAD_SIZE = 1446;
        
    // rtp => rtmp
    private byte[] sps1;
    private byte[] pps;
    private boolean sentSeq;
    private long lastFIRTime;
    private long startTs;
    private long startTm;
    private long startRelativeTime;
    private List<RtpPacketWrapper> packetsQueue;
        
    // rtmp => rtp
    private int lenSize;
    private boolean spsSent = false;
    private boolean ppsSent = false;

    private ConverterObserver observer;
        
    public H264ProtocolConverter() {
        resetConverter();
        startRelativeTime = System.currentTimeMillis();
        observer = null;
    }

    public void resetConverter() {
        packetsQueue = new ArrayList<RtpPacketWrapper>();
        lastFIRTime = System.currentTimeMillis();
        sps1 = new byte[0];
        pps = new byte[0];
        sentSeq = false;
        startTs = -1;
        startTm = -1;
    } 

    /* Check whether there are key frames in the queue */
    private boolean queueHasKeyFrames(){
        if(packetsQueue.size() < 1) return false;
        // Since the queue will contain a single type of frames it is enough to check the first one
        if(packetsQueue.get(0).nalType == 5)
            return true;
        return false;
    }

    /* Drop the packets in the queue, leaving the last one */
    private void dropQueue() {
        if(packetsQueue.size() < 1) return;
        RtpPacketWrapper last = packetsQueue.get(packetsQueue.size() - 1);
        packetsQueue.clear();
        packetsQueue.add(last);
    }

    /* Drop the last packet in the queue */
    private void dropLast() {
        if(packetsQueue.size() < 1) return;
        packetsQueue.remove(packetsQueue.size() -1);
    }

    /* Check whether all packets in the queue have the same timestamp.
     * If they have different timestamps, they do not belong to the same frame.
     * this means that some packages are coming out of order, and will be dropped.
     * We should be careful not to drop key-frames, as this would freeze the video.
     */
    private void checkQueueTimestamps() {
        RtpPacketWrapper last = packetsQueue.get(packetsQueue.size() - 1);
        RtpPacketWrapper preLast = packetsQueue.get(packetsQueue.size() - 2);
        RtpPacket lastPck = last.packet;
        RtpPacket preLastPck = preLast.packet;

        if (lastPck.getTimestamp() != preLastPck.getTimestamp()) {
            log.warn("New packet has different ts. old ts={} new ts={}",
                    preLastPck.getTimestamp(), lastPck.getTimestamp());

            if(queueHasKeyFrames() && (last.nalType == 5)) {
                // Queue has key frames and a different key frame arrived, keep the newest
                if(lastPck.getTimestamp() < preLastPck.getTimestamp()){
                    log.debug("Queue has key frames and an older key frame arrived, dropping frame");
                    dropLast();
                }
                else {
                    log.debug("Queue has key frames and a newer key frame arrived, dropping queue");
                    dropQueue();
                }
            } else if (queueHasKeyFrames()) {
                log.debug("Queue has key frames and a frame type [{}] arrived, dropping frame", last.nalType);
                dropLast();
            } else if(last.nalType == 5) {
                log.debug("Queue has key frames and key frame arrived, dropping queue");
                dropQueue();
            }
        }
    }

    @Override
	public List<RTMPPacketInfo> rtpToRTMP(RtpPacket packet) {

        List<RTMPPacketInfo> result = new ArrayList<RTMPPacketInfo>();
        byte[] payload = packet.getPayload();
        int nalType = payload[0] & 0x1f;
        byte[] naldata = null;

        switch (nalType) {
        case 7: // SPS
                sps1 = payload;
                //log.debug("$$ SPS received: " + Arrays.toString(sps1));
                break;
        case 8: // PPS
                pps = payload;
                //log.debug("$$ PPS received: " + Arrays.toString(pps));
                break;
        default:
                if (payload.length > 1) {
                        if (nalType == 24) { // for cisco phones
                                payload = Arrays.copyOfRange(payload, 1, payload.length);
                                while (payload.length > 0) {
                                        int size = payload[1];
                                        payload = Arrays.copyOfRange(payload, 2, payload.length);
                                        naldata = Arrays.copyOf(payload, size);
                                        payload = Arrays.copyOfRange(payload, size, payload.length);
                                        int nt = naldata[0] & 0x1f;
                                        switch (nt) {
                                        case 7:
                                                sps1 = naldata;
                                                //log.debug("SPS received: " + Arrays.toString(sps1));
                                                break;
                                        case 8:
                                                pps = naldata;
                                                //log.debug("PPS received: " + Arrays.toString(pps));
                                                break;
                                        default:
                                                break;
                                        }
                                }
                        }
                        
                        if (nalType == 1 || nalType == 5 || nalType == 28 || nalType == 24) {
                                packetsQueue.add(new RtpPacketWrapper(packet, nalType));
                        }
                }
                break;
        }
        
        if (packetsQueue.size() > 1) {
            checkQueueTimestamps();
        }
        
        // marker means the end of the frame
        if (packet.getPacket()[1] < 0 && !packetsQueue.isEmpty()) {
                int realNri = 0;
                nalType = 0;
                List<ByteArrayBuilder> payloads = new ArrayList<ByteArrayBuilder>();
                ByteArrayBuilder newdata = null;
                List<ByteArrayBuilder> pendingData = new ArrayList<ByteArrayBuilder>();
                
                for (RtpPacketWrapper q: packetsQueue) {
                        int length = 0;
                        switch (q.nalType) {
				            case 1:
				            case 5:
				                    if (newdata == null) {
				                            nalType = q.nalType;
				                            // first byte: 0x17 for intra-frame, 0x27 for non-intra frame
				                            // second byte: 0x01 for picture data
				                            newdata = new ByteArrayBuilder(new byte[]{(byte) (q.nalType == 5? 0x17: 0x27), 1, 0, 0, 0});
				                    }
				                    length = q.packet.getPayload().length;
				                    newdata.putArray((byte) (length >>> 24), (byte) (length >>> 16), (byte) (length >>> 8), (byte) length);
				                    newdata.putArray(q.packet.getPayload());
				                    break;
				            case 24:
				                    payload = Arrays.copyOfRange(payload, 1, payload.length);
				                    while (payload.length > 0) {
				                            int size = payload[0];
				                            payload = Arrays.copyOfRange(payload, 2, payload.length);
				                            naldata = Arrays.copyOf(payload, size);
				                            payload = Arrays.copyOfRange(payload, size, payload.length);
				                            int nt = naldata[0] & 0x1f;
				                            if (nt == 5 || nt == 1) {
				                                    if (newdata == null) {
				                                            nalType = nt;
				                                            // first byte: 0x17 for intra-frame, 0x27 for non-intra frame
				                                            // second byte: 0x01 for picture data
				                                            newdata = new ByteArrayBuilder(new byte[]{(byte) (nt == 5? 0x17: 0x27), 1, 0, 0, 0});
				                                    }
				                                    length = naldata.length;
				                                    newdata.putArray((byte) (length >>> 24), (byte) (length >>> 16), (byte) (length >>> 8), (byte) length);
				                                    newdata.putArray(naldata);
				                            }
				                    }
				                    break;

				            case 28:
				                    if (newdata == null) {
				                            nalType = q.packet.getPayload()[1] & 0x1f;
				                            realNri = q.packet.getPayload()[0] & 0x60;
				                            // first byte: 0x17 for intra-frame, 0x27 for non-intra frame
				                            // second byte: 0x01 for picture data
				                            newdata = new ByteArrayBuilder(new byte[]{(byte) (nalType == 5? 0x17: 0x27), 1, 0, 0, 0});
				                    }
				                    pendingData.add(new ByteArrayBuilder(Arrays.copyOfRange(q.packet.getPayload(), 2, q.packet.getPayload().length)));
				                    if ((q.packet.getPayload()[1] & 0x40) == 0x40) {
				                            ByteArrayBuilder remaining = new ByteArrayBuilder((byte) (nalType | realNri));
				                            for (ByteArrayBuilder pd: pendingData) {
				                                    remaining.putArray(pd.buildArray());
				                            }
				                            pendingData.clear();
				                            length = remaining.getLength();
				                            newdata.putArray((byte) (length >>> 24), (byte) (length >>> 16), (byte) (length >>> 8), (byte) length);
				                            newdata.putArray(remaining.buildArray());
				                    } 
				                    else {
				                            continue;
				                    }
				                    break;

				            default:
				                    break;
                        }
                }


                packetsQueue.clear();
                
                if (newdata != null) {
                        payloads.add(newdata);
                }
                
                if (!sentSeq && nalType != 5 && pps.length > 0 && sps1.length > 0 || sps1.length == 0 || pps.length == 0) {
                        packetsQueue.clear();
                        if (System.currentTimeMillis() - lastFIRTime > 5000) {
                                lastFIRTime = System.currentTimeMillis();
                                requestFIR();
                        }
                } 
                else {
                        if (pps.length > 0 && sps1.length > 0 && !sentSeq && nalType == 5) {
                                sentSeq = true;
                        }
                        
                        // calculate timestamp
                        if (startTs == -1) {
                                startTs = packet.getTimestamp();
                        }
                        if (startTm == -1) {
                                startTm = System.currentTimeMillis() - startRelativeTime;
                        }

                        long tm = startTm + (packet.getTimestamp() - startTs) / 90; // 90 = bitrate / 1000
                        if (nalType == 5 && payloads.size() > 0) {
                                ByteArrayBuilder data = new ByteArrayBuilder();
                                // first byte: 0x17 for intra-frame
                                // second byte: 0x00 for configuration data
                                data.putArray(new byte[]{0x17, 0, 0, 0, 0, 1});
                                data.putArray(Arrays.copyOfRange(sps1, 1, 4));
                                data.putArray((byte) 0xff, (byte) 0xe1, (byte) (sps1.length >>> 8), (byte) sps1.length);
                                data.putArray(sps1);
                                data.putArray((byte) 1, (byte) (pps.length >>> 8), (byte) pps.length);
                                data.putArray(pps);
                                payloads.add(0, data);
                        }
                        
                        for (ByteArrayBuilder bba: payloads) {
                                result.add(new RTMPPacketInfo(bba.buildArray(), tm));
                        }
                }
        }
    	return result;
	}

    @Override
    public void setConverterObserver(ConverterObserver observer) {
        this.observer = observer;
    }


    protected void requestFIR() {
        log.debug("Requesting FIR...");
        if(this.observer != null)
            this.observer.onFirRequest(); 
    }


    @Override
    public List<RtpPacket> rtmpToRTP(byte data[], long ts) {

        List<RtpPacket> result = new ArrayList<RtpPacket>();
        //long ts1 = ts * codec.getSampleRate() / 1000;
        long ts1 = ts * 90000 / 1000;
        if (data[0] == 0x17 && data[1] == 0) {            
            byte[] pdata = Arrays.copyOfRange(data, 2, data.length);
            int cfgVer = pdata[3];
            if (cfgVer == 1) {
                int lenSize = pdata[7] & 0x03 + 1;
                int numSPS = pdata[8] & 0x1f;
                pdata = Arrays.copyOfRange(pdata, 9, pdata.length);
                byte[] sps = null;
                for (int i = 0; i < numSPS; i++) {
                    int lenSPS = (pdata[0] & 0xff) << 8 | pdata[1] & 0xff;
                    pdata = Arrays.copyOfRange(pdata, 2, pdata.length);
                    if (sps == null) {
                        sps = Arrays.copyOf(pdata, lenSPS);
                    }
                    pdata = Arrays.copyOfRange(pdata, lenSPS, pdata.length);
                }
                int numPPS = pdata[0];
                pdata = Arrays.copyOfRange(pdata, 1, pdata.length);
                byte[] pps = null;
                for (int i = 0; i < numPPS; i++) {
                    int lenPPS = (pdata[0] & 0xff) << 8 | pdata[1] & 0xff;
                    pdata = Arrays.copyOfRange(pdata, 2, pdata.length);
                    if (pps == null) {
                        pps = Arrays.copyOf(pdata, lenPPS);
                    }
                    pdata = Arrays.copyOfRange(pdata, lenPPS, pdata.length);
                }
                this.lenSize = lenSize;
                if (sps != null) {
                    spsSent = true;
                    byte[] buffer = new byte[sps.length + 12];
                    RtpPacket packet = new RtpPacket(buffer, 0);
                    packet.setPayload(sps, sps.length);
                    packet.setTimestamp(ts1);
                    buffer[1] = (byte) 0xe3;
                    result.add(packet);
                }
                if (pps != null) {
                    ppsSent = true;
                    byte[] buffer = new byte[pps.length + 12];
                    RtpPacket packet = new RtpPacket(buffer, 0);
                    packet.setPayload(pps, pps.length);
                    packet.setTimestamp(ts1);
                    buffer[1] = (byte) 0xe3;
                    result.add(packet);
                }
            } else {
                log.debug("Unsuported cfgVer=" + cfgVer);
            }
        } else if ((data[0] == 0x17 || data[0] == 0x27) && data[1] == 1) {            
            if (spsSent && ppsSent) {
                List<ByteArrayBuilder> nals = new ArrayList<ByteArrayBuilder>();
                byte[] pdata = Arrays.copyOfRange(data, 5, data.length);
                while (pdata.length > 0) {
                    int nalSize = 0;
                    switch (lenSize) {
                    case 1:
                        nalSize = pdata[lenSize - 1] & 0xff;
                        break;
                    case 2:
                        nalSize = (pdata[lenSize - 2] & 0xff) << 8 | pdata[lenSize - 1] & 0xff;
                        break;
                    case 4:
                        nalSize = (pdata[lenSize - 4] & 0xff) << 24 |
                                  (pdata[lenSize - 3] & 0xff) << 16 | 
                                  (pdata[lenSize - 2] & 0xff) << 8  | 
                                  (pdata[lenSize - 1] & 0xff);
                        break;
                    default:
                        throw new RuntimeException("Invalid length size: " + lenSize);
                    }
                    ByteArrayBuilder nalData = new ByteArrayBuilder(Arrays.copyOfRange(pdata, lenSize, lenSize + nalSize));
                    nals.add(nalData);
                    pdata = Arrays.copyOfRange(pdata, lenSize + nalSize, pdata.length);
                }
                if (nals.size() > 0) {
                    byte[] remaining = nals.get(nals.size() - 1).buildArray();
                    int nalType = remaining[0] & 0x1f;
                    int nri = remaining[0] & 0x60;
                    if (nalType == 5 || nalType == 1) {
                        if (remaining.length < MAX_RTP_PAYLOAD_SIZE) {
                            byte[] buffer = new byte[remaining.length + 12];
                            RtpPacket packet = new RtpPacket(buffer, 0);
                            packet.setPayload(remaining, remaining.length);
                            packet.setTimestamp(ts1);
                            buffer[1] = (byte) 0xe3; // marker and payload type
                            result.add(packet);
                        } else {
                            byte start = (byte) 0x80;
                            remaining = Arrays.copyOfRange(remaining, 1, remaining.length);
                            while (remaining.length > 0) {
                                pdata = Arrays.copyOf(remaining, Math.min(MAX_RTP_PAYLOAD_SIZE - 2, remaining.length));
                                remaining = Arrays.copyOfRange(remaining, Math.min(MAX_RTP_PAYLOAD_SIZE - 2, remaining.length), remaining.length);
                                byte end = (byte) ((remaining.length > 0)? 0: 0x40);
                                ByteArrayBuilder payload = new ByteArrayBuilder((byte) (nri | 28), (byte) (start | end | nalType));
                                payload.putArray(pdata);
                                start = 0;
                                
                                byte[] buffer = new byte[payload.getLength() + 12];
                                RtpPacket packet = new RtpPacket(buffer, 0);
                                packet.setPayload(payload.buildArray(), payload.getLength());
                                packet.setTimestamp(ts1);
                                buffer[1] = (byte) ((end == 0x40)? 0xe3: 0x63);
                                result.add(packet);
                            }
                        }
                    }
                }
            }
        } else {
            log.debug("Missing rtmp data");
        }

        return result;

    }






}

