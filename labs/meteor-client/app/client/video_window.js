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
      constraints: {
        audio: true,
        video: true
      }
    }
  });

  // Create new Session and append it to list
  var ui = createNewSessionUI(uri, session);
}

function createNewSessionUI(uri, session) {
  var sessionUI = {};

  sessionUI.video          = document.getElementById('video-window');
  sessionUI.renderHint     = {
    remote: {
      video: sessionUI.video
    }
  }

    session.on('accepted', function (a,b) {
      console.log("Session accepted");
      session.mediaHandler.render(sessionUI.renderHint);
    });

    session.mediaHandler.on('addStream', function (a,b) {
      console.log("Session addStream");
      session.mediaHandler.render(sessionUI.renderHint);
    });

    session.on('rejected', function (a,b) {
        console.warn("Session rejected");
    });

    session.on('failed', function (a,b) {
        console.warn("Session failed");
    });
}
