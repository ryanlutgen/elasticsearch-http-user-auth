[![Build Status](https://travis-ci.org/elasticfence/elasticsearch-http-user-auth.svg?branch=master)](https://travis-ci.org/elasticfence/elasticsearch-http-user-auth)

![](http://i.imgur.com/OFFgrm8.png?1)

# Elasticfence - Elasticsearch HTTP Basic User Auth plugin

Elasticsearch user authentication plugin with http basic auth and IP ACL

This plugin provides user authentication APIs and a User management web console. 

## Installation 
<pre>
bin/plugin install https://raw.githubusercontent.com/elasticfence/elasticsearch-http-user-auth/2.4.2/jar/elasticfence-2.4.2-SNAPSHOT.zip
</pre>

#### Build with Maven
<pre>
mvn package clean
bin/plugin install file:///path/to/repo/jar/elasticfence-2.4.2-SNAPSHOT.zip
</pre>

## Configuration
Add following lines to elasticsearch.yml:
<pre>
elasticfence.disabled: false
elasticfence.root.password: rootpassword
</pre>

To disable the plugin set `elasticfence.disabled` to `true`  

To set the root password on each start use `http.user.auth.root.password`   
**Only the root user can access ES's root APIs (like /_cat, /_cluster) and all indices.**

Other users can access URLs under their own indices that are specified with this plugin's API.  

### Basic IP ACL
IPs contained in whitelist/blacklist arrays will bypass authentication
<pre>
elasticfence.whitelist: ["127.0.0.1", "10.0.0.1"]
elasticfence.blacklist: ["127.0.0.2", "10.0.0.99"]
</pre>

## Add username and password on HTTP requests
The authentication method of this plugin is Basic Authentication. Therefore, you should add your username and password on URL string. For example: 

<pre>
http://root:rootpassword@your.elasticsearch.hostname:9200/
</pre>

###### CURL
<pre>
curl -u root:rootpassword http://your.elasticsearch.hostname:9200/
</pre>
```javascript
{
  "status" : 200,
  "name" : "Piranha",
  "cluster_name" : "elastic1",
  "version" : {
    "number" : "1.7.3",
    "build_hash" : "05d4530971ef0ea46d0f4fa6ee64dbc8df659682",
    "build_timestamp" : "2015-10-15T09:14:17Z",
    "build_snapshot" : false,
    "lucene_version" : "4.10.4"
  },
  "tagline" : "You Know, for Search"
}
```

Plugins using ES's REST API also have to be set root password in their configurations.

The ways of configuring Marvel and Kibana 4 are below: 

#### Marvel 
elasticsearch.yml:
<pre>
marvel.agent.exporter.es.hosts: ["root:rootpassword@127.0.0.1:9200"]
</pre>

#### Kibana 4
Add index filter "/.kibana" to a <b>your_custom_username</b> which you created on Elasticfence and set it in kibana.yml:
<pre>
elasticsearch.username: <b>your_custom_username</b>
elasticsearch.password: <b>your_custom_password</b>
</pre>

Add permissions to your <b>kibana</b> users using regex filters: 
<pre>
/index.*,/_.*,/.kibana,/
</pre>

##### Multiple Kibana Instances
To run multiple Kibana instances in the cluster, add/modify the following lines in the associated kibana.yml files:
<pre>
server.port
kibana.index
</pre>

- These values should be unique for each Kibana instance
- kibana.index should start with a period '`.`', ex: `.kibana-instance2`

Set the `elasticsearch.username` and `elasticsearch.password` to different values for each Kibana instance.

Then add the following line to the `elasticsearch.yml` file:
<pre>
elasticfence.kibana.indices: [".kibana", ".kibana-instance2"]
</pre>
- This defines what indices represent Kibana indices.  This should be an array of all of the `kibana.index` values.
    - This setting can also be an array of length 1 if the a single instance of Kibana does not use the default `.kibana` index.
- This setting is not needed if one instance of Kibana is used and it is using the default `kibana.index` value.

## User Management Console

This plugin provides a web console which manages users. 
<pre>
http://your.elasticsearch.hostname:9200/_plugin/elasticfence/index.html
</pre>

## User Management API
This plugin provides a web API to manage users and permissions.
![](http://i.imgur.com/r26mGAl.png)

##### Add User:
<pre>
http://your.elasticsearch.hostname:9200/_httpuserauth?mode=adduser&username=admin&password=somepass
</pre>

##### Add Index Permissions:
<pre>
http://your.elasticsearch.hostname:9200/_httpuserauth?mode=addindex&username=admin&password=somepass&index=index*
</pre>

##### Update Index Permissions:
<pre>
http://your.elasticsearch.hostname:9200/_httpuserauth?mode=updateindex&username=admin&index=index-*
</pre>

##### Delete User:
<pre>
http://your.elasticsearch.hostname:9200/_httpuserauth?mode=deleteuser&username=admin
</pre>

##### List User(s):
<pre>
http://your.elasticsearch.hostname:9200/_httpuserauth?mode=list
</pre>
```javascript
[{ 
  "username":"admin",
  "password":"7080bfe27990021c562398e79823h920e9a38aa5d3b10c5ff5d8c498305",
  "indices":["/_*"],
  "created":"2015-11-06T21:57:21+0100"
}]
```
