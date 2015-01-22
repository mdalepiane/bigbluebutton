bbb-external-api-caller
=======================

A node.js application that listens for events on BigBlueButton, perform calls
on an external API and publish result back to redis, so BigBlueButton may use
it.


Event mapping
-------------

The application subscribes to redis events and check them against the events
mapped in config. When an event is mapped to an external API call, the call
is performed and the result is published back to redis.

Event maps must contain the following information:
* channel: redis channel
* event_name: redis event name
* api_call: external API call used for the event
* response: redis event name for the response
* response_channel: redis channel for the response

External API Call Format
------------------------

The external API calls follow BigBlueButton API call format.

Everything within the event payload is included as parameters for the http call.

Result from API call
--------------------

The result from the API call is published back to redis in the channel specified
in the mapping, with the event name specified.

Everything from the payload of the original event is included in the response
payload, as well as the response data. If there is a field with the same name
in the original payload and in the response data, the original content is
overwritten by the content within the call response.

Development
-----------

1. Install node. You can use [NVM](https://github.com/creationix/nvm) if you need multiple versions of node or install it from source. To install from source, first check the exact version you need on `package.json` and replace the all `vX.X.X` by the correct version when running the commands below.

    ```bash
wget http://nodejs.org/dist/vX.X.X/node-vX.X.X.tar.gz
tar -xvf node-vX.X.X.tar.gz
cd node-vX.X.X/
./configure
make
sudo make install
    ```

2. Install the dependencies: `npm install`

3. Copy and edit the configuration file: `cp config_local.coffee.example config_local.coffee`

4. Run the application with:

    ```bash
node app.js
    ```

Production
----------

1. Install node. First check the exact version you need on `package.json` and replace the all `vX.X.X` by the correct version in the commands below.

    ```bash
wget http://nodejs.org/dist/vX.X.X/node-vX.X.X.tar.gz
tar -xvf node-vX.X.X.tar.gz
cd node-vX.X.X/
./configure
make
sudo make install
    ```

2. Copy the entire bbb-external-api-caller directory to `/usr/local/bigbluebutton/bbb-external-api-caller` and cd into it.

3. Install the dependencies: `npm install`

4. Copy and edit the configuration file to adapt to your server: `cp config_local.coffee.example config_local.coffee`.

6. Copy upstart's configuration file and make sure its permissions are ok:

    ```bash
sudo cp config/upstart-bbb-external-api-caller.conf /etc/init/bbb-external-api-caller.conf
sudo chown root:root /etc/init/bbb-external-api-caller.conf
    ```

    Open the file and edit it. You might need to change things like the user used to run the application.

7. Copy monit's configuration file and make sure its permissions are ok:

    ```bash
sudo cp config/monit-bbb-external-api-caller /etc/monit/conf.d/bbb-external-api-caller
sudo chown root:root /etc/monit/conf.d/bbb-external-api-caller
    ```

    Open the file and edit it. You might need to change things like the port used by the application.

8. Copy logrotate's configuration file and install it:

    ```bash
sudo cp config/bbb-external-api-caller.logrotate /etc/logrotate.d/bbb-external-api-caller
sudo chown root:root /etc/logrotate.d/bbb-external-api-caller
sudo chmod 644 /etc/logrotate.d/bbb-external-api-caller
sudo logrotate -s /var/lib/logrotate/status /etc/logrotate.d/bbb-external-api-caller
    ```

9. Start the application:

    ```bash
sudo service bbb-external-api-caller start
sudo service bbb-external-api-caller stop
    ```
