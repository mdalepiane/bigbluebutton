redis = require("redis")
Logger = require("./logger")
ApiCaller = require("./api_caller")

config = require("./config")

# Class that defines the application.
# Listens for events on redis and call external API when
# a call for the event is defined

module.exports = class Application
  constructor: ->
    @subscriberEvents = redis.createClient()

  start: ->
    @_subscribeToEvents()

  # Subscribe to redis events
  _subscribeToEvents: ->
    @subscriberEvents.on "psubscribe", (channel, count) ->
      Logger.info "EXTERNAL API CALLER: subscribed to " + channel

    @subscriberEvents.on "pmessage", (pattern, channel, message) =>
      message = JSON.parse(message)
      if @_filterMessage(channel, message)
        Logger.info "EXTERNAL API CALLER: processing message on [#{channel}]:", JSON.stringify(message)
        @_processCall(message)

    @subscriberEvents.psubscribe "bigbluebutton:*"

  # Make the API call
  _processCall: (message) ->
    caller = new ApiCaller(message)
    caller.call()

  # Returns whether the message read from redis is mapped to an API call or not.
  _filterMessage: (channel, message) ->
    for call in config.api.call_map
      if channel? and message.header?.name? and
         call.channel.match(channel) and call.event_name.match(message.header?.name)
          return true
    false
