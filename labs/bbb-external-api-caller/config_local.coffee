# Local configuration file

config = {}

config.api = {}

# API configs
config.api.sharedSecret = "ZPvUIGwfKGs61DTwCuBvBTll8QBwKTij"
config.api.apiServer = "10.0.3.1"
config.api.apiPath = "/bigbluebutton/api"
config.api.apiPort = "3000"

# Map bbb events to API calls
config.api.call_map = [
  { channel: "bigbluebutton:from-bbb-apps:video", event_name: "get_stream_path_request", api_call: "getStreamPath", response: "get_stream_path_reply", response_channel: "bigbluebutton:to-bbb-apps:video" },
]

module.exports = config
