config = {};

config["displayName"] = "HTML5 Client";
config["wsServers"] = "ws://10.0.3.97/ws";

// TODO: get voice bridge
var uri = "72014@10.0.3.97";

ua = new SIP.UA(config);

function inviteSubmit() {
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

  console.log(sessionUI);

    session.on('accepted', function () {
      session.mediaHandler.render(sessionUI.renderHint);
    });

    session.mediaHandler.on('addStream', function () {
      session.mediaHandler.render(sessionUI.renderHint);
    });
}

ua.on('invite', function (session) {
  console.log("UA invite");
  createNewSessionUI(session.remoteIdentity.uri, session);
});

inviteSubmit();
