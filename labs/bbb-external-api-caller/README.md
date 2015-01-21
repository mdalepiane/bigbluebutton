bbb-external-api-caller
=======================

A node.js application that listens for events on BigBlueButton, perform calls
on an external API and publish result back to redis, so BigBlueButton may use
it.


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
