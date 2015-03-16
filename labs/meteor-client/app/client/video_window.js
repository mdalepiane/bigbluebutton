inviteSubmit = function() {
  var userName = BBB.getMyUserName();
  var serverIP = window.location.hostname;

  var configuration = {
    uri: 'sip:' + userName + '@' + serverIP,
    wsServers: 'ws://' + serverIP + '/ws',
    displayName: BBB.getMyUserID() + "-bbbID-" + userName,
    register: false,
    traceSip: true,
    autostart: false,
    userAgentString: "BigBlueButton"
    // TODO: Not sure what to do with these:
//    stunServers: stunsConfig['stunServers'],
//    turnServers: stunsConfig['turnServers']
  };
  
  var uri = BBB.getMyVoiceBridge() + "@" + serverIP;

  var ua = new SIP.UA(configuration);
  ua.start();

  // Send invite
  var session = ua.invite(uri, {
      media: {
//          stream: new webkitMediaStream(),
          render: {
              remote: {
                  video: document.querySelector('video')
              }
          }
      },
      RTCConstraints: {
          mandatory: {
              OfferToReceiveAudio: true,
              OfferToReceiveVideo: true
          }
      }
  });
}
