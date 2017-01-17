[![Build Status](https://travis-ci.org/elasticfence/elasticsearch-http-user-auth.svg?branch=5.1.1)](https://travis-ci.org/elasticfence/elasticsearch-http-user-auth)

![](http://i.imgur.com/OFFgrm8.png?1)

# Elasticfence - Elasticsearch HTTP Basic User Auth plugin

Elasticsearch user authentication plugin with http basic auth and IP ACL

This plugin provides user authentication APIs and a User management web console. 

## Installation 
<pre>
bin/plugin install https://raw.githubusercontent.com/elasticfence/elasticsearch-http-user-auth/5.1.1/jar/elasticfence-5.1.1-SNAPSHOT.zip
</pre>

#### Build with Maven
<pre>
mvn package clean
bin/plugin install file:///path/to/repo/jar/elasticfence-5.1.1-SNAPSHOT.zip
</pre>

## Configuration

### Enabling/Disabling Elasticfence
<pre>
elasticfence.disabled: false
</pre>

To disable the plugin set `elasticfence.disabled` to `true`

### Root Access
<pre>
elasticfence.root.password: rootpassword
</pre>

To set the root password on each start use `elasticfence.root.password`

**Only the root user can access ES's root APIs (like /_cat, /_cluster) and all indices.**  Other users can access URLs under their own indices that are specified with this plugin's API.

### Sharding Scheme
<pre>
elasticfence.number_of_shards: 1
elasticfence.number_of_replicas: 3
</pre>

Omit these config options to use the Elasticsearch defaults (currently 5 and 1 respectively), otherwise set them according to desired level of redundancy and cluster scheme.

### Basic IP ACL
IPs contained in whitelist array will bypass authentication, blacklisted IPs will be blocked.  All other IPs will show an authentication window.
<pre>
elasticfence.whitelist: ["127.0.0.1", "10.0.0.1"]
elasticfence.blacklist: ["127.0.0.2", "10.0.0.99"]
</pre>

To block all IPs that are not in the whitelist, use the following option for `elasticfence.blacklist`
<pre>
elasticfence.blacklist: ["*"]
</pre>

### Kibana 4
Add index filter "/.kibana" to a <b>your_custom_username</b> which you created on Elasticfence and set it in kibana.yml:
<pre>
elasticsearch.username: <b>your_custom_username</b>
elasticsearch.password: <b>your_custom_password</b>
</pre>

Add permissions to your <b>kibana</b> users using regex filters: 
<pre>
/index.*,/_.*,/.kibana,/
</pre>

#### Kibana 4.x Auth Plugin
To facilitate users and improve security, the optional Kibana Auth plugin can be deployed alongside Elastifence:
```
bin/kibana plugin --install kibana-auth-plugin -u https://github.com/elasticfence/kibana-auth-elasticfence/releases/download/snapshot/kauth-latest.tar.gz
```
<img src="https://cloud.githubusercontent.com/assets/1423657/18619991/c47b632e-7e09-11e6-9eff-7b8324ad04c6.png" width="300"/>

---------------

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

## User Management Console

## User Management API
This plugin provides a web API to manage users and permissions.

##### Add User:
<pre>
http://your.elasticsearch.hostname:9200/_httpuserauth?mode=adduser&username=admin&password=somepass
</pre>

##### Add Index Permissions:
<pre>
http://your.elasticsearch.hostname:9200/_httpuserauth?mode=addindex&username=admin&index=index*&read=true&write=true&delete=true
</pre>

##### Update Index Permissions:
<pre>
http://your.elasticsearch.hostname:9200/_httpuserauth?mode=updateindex&username=admin&index=index-*&read=false&write=false&delete=false
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
