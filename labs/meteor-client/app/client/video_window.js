inviteSubmit = function() {
  var ua;
  
  var configuration = {
    //uri: 'sip:' + encodeURIComponent(username) + '@' + server,
    wsServers: 'ws://10.0.3.97/ws',
    displayName: BBB.getMyUserID() + "-bbbID-" + BBB.getMyUserName(),
    register: false,
    traceSip: true,
    autostart: false,
    userAgentString: "BigBlueButton"
//    stunServers: stunsConfig['stunServers'],
//    turnServers: stunsConfig['turnServers']
  };
  
  // TODO: get voice bridge
  var uri = "72014@10.0.3.97";
  configuration["uri"] = uri;

  console.log("CONFIGURATION");
  console.log(configuration);
  ua = new SIP.UA(configuration);
  ua.start();

//  ua.on('invite', function (session) {
//    console.log("UA invite");
//    createNewSessionUI(session.remoteIdentity.uri, session);
//  });

  console.log("inviteSubmit START");
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
  console.log("inviteSubmit END");
}

function createNewSessionUI(uri, session) {
  console.log("CREATING UI START");
  var sessionUI = {};

  sessionUI.video          = document.getElementById('video-window');
  sessionUI.renderHint     = {
    remote: {
      video: sessionUI.video
    }
  }

    session.on('accepted', function (a,b) {
        console.log("SESSION ACCEPTED");
        console.log(a + b);
      session.mediaHandler.render(sessionUI.renderHint);
    });

    session.mediaHandler.on('addStream', function (a,b) {
        console.log("SESSION ADDSTREAM");
        console.log(a + b);
      session.mediaHandler.render(sessionUI.renderHint);
    });

    session.on('rejected', function (a,b) {
        console.log(a + b);
        console.log("SESSION REJECTED");
    });

    session.on('failed', function (a,b) {
        console.log(a + b);
        console.log("SESSION FALIED");
    });
  console.log("CREATING UI END");
}
