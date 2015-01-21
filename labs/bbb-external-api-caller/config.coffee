# Global configuration file

# load the local configs
config = require("./config_local")

# API configs
config.api or= {}
config.api.sharedSecret or= "33e06642a13942004fd83b3ba6e4104a"
config.api.apiServer or= "127.0.0.1"
config.api.apiPath or= "/bigbluebutton/api"
config.api.apiPort or= "3000"

# Map bbb events to API calls
#  channel: redis channel
#  event_name: redis event name
#  api_call: external API call used for the event
#  response: redis event name for the response
#  response_channel: redis channel for the response 
config.api.call_map or= [
  { channel: "bigbluebutton:from-bbb-apps:video", event_name: "get_stream_path_request", api_call: "getStreamPath", response: "get_stream_path_reply", response_channel: "bigbluebutton:to-bbb-apps:video" },
]

module.exports = config
