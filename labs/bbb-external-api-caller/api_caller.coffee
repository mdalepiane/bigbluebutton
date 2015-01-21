request = require("request")
redis = require("redis")
Utils = require("./utils")
Logger = require("./logger")

config = require("./config")

# Class used to perform the API calls and publish the response into redis.
module.exports = class ApiCaller

  constructor: (message) ->
    @message = message
    @apiCall = @_getApiCall(message)
    @redis = redis.createClient()

  call: ->
    parameters = @_toParameters(@message)

    callURL = "http://#{config.api.apiServer}:#{config.api.apiPort}#{config.api.apiPath}/#{@apiCall.api_call}?#{parameters}"

    Logger.info "callURL " + callURL

    # calculate the checksum
    checksum = Utils.checksum("#{@apiCall.api_call}#{parameters}#{config.api.sharedSecret}")

    # get the final callback URL, including the checksum
    callURL += "&checksum=#{checksum}"

    requestOptions =
      followRedirect: true
      maxRedirects: 10
      uri: callURL
      method: "GET"

    callback = @_handleResponse
    request requestOptions, (error, response, body) ->
      if error? or not (response?.statusCode >= 200 and response?.statusCode < 300)
        Logger.warn "xx> Error in the callback call to: [#{requestOptions.uri}]"
        Logger.warn "xx> Error:", error
        Logger.warn "xx> Status:", response?.statusCode
        callback error, false
      else
        Logger.info "==> Successful callback call to: [#{requestOptions.uri}]"
        callback body, true

  # Get the API call for the event received
  _getApiCall: (message) ->
    for call in config.api.call_map
      if message.header?.name? and call.event_name.match(message.header?.name)
         return call

  # Use all parameters from the event payload as parameters for the HTTP call
  _toParameters: (message) ->
    Logger.info message.payload
    parameters = ""
    for param, val of message.payload
      parameters += "#{param}=#{val}&"
    parameters.replace(/&$/g, "")

  # Handle the response, publish response data into redis
  _handleResponse: (response, result) =>
    if result is true
      Logger.info(response)
      response = JSON.parse response
      Logger.info "Handling response for #{@message.header.name}"
      Logger.info "Send response to #{@apiCall.response} at #{@apiCall.response_channel}"
      json = @_responseToJson(response)
      Logger.info json
      @redis.publish(@apiCall.response_channel, json)
    else
      #TODO: Handle error - retry

  # Create a redis event using the call response and the original event data
  _responseToJson: (response) ->
    json = {"header":{"name":@apiCall.response}, "payload":{}}

    for name, val of @message.payload
      json.payload[name] = val
    for name, val of response
      json.payload[name] = val
    JSON.stringify(json)
