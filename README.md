# Logstash Adabas Auditing Input File Plugin

This is a Java plugin for [Logstash](https://github.com/elastic/logstash). The plugin checks the `directory` for new captured Adabas Auditing messsages and process them.
To parse the Adabas Auditing messages it is required that the schema (metadata) of the data is available for the plugin. These schemas are provided as an Adabas Auditing message
and is stored locally at the `metaDir` location. So, it is essential to process the message with schemata first which is available for example data in the `assets` folder. 
When the plugin is started, it also starts a REST server for the schemata when the `restURL` contains `localhost`. 

## Build
The build of this plugin requires the access to an installation of Logstash.

1. Download Logstash from https://www.elastic.co/downloads/logstash
2. Copy the files **rubyUtils.gradle** and **versions.yml** from Github repository https://www.elastic.co/downloads/logstash to directory where you installed Logstash
3. Clone this repository
4. Set the property variable **LOGSTASH_CORE_PATH**. This could be done in gradle.properties file
5. Assemble plugin with the command `./gradlew assemble gem`

After that successful build a file **logstash-input-adabas_auditing_file_input-<version>-java.gem** is created in the root directory of the project.

See also [How to write a Java input plugin](https://www.elastic.co/guide/en/logstash/current/java-input-plugin.html).

## Install Plugin
To install the plugin use the command 
```
logstash install --no-verify --local <full-path>/logstash-input-adabas_auditing_file_input-<version>-java.gem
```

## Run Logstash
Execute the command `logstash -f <file>` where `<file>`is your Logstash configuration file. An example is below.

## Plugin Configuration Example
This configuration reads the data from the Adabas Auditing Server and write the data to `stdout`.

```
input {
  adabas_auditing_file_input { 
    directory => "/Users/ger/tmp/ala"
    metaDir => "/Users/ger/tmp/meta"
    type => "adabas"
  }
}
output {
  stdout { 
    codec => rubydebug
  }
}
```

## Plugin Parameter
| Parameter     | Description                     | Type   | Default Value                         |
| --------------| ------------------------------- | ------ | ------------------------------------- |
| directory     | Directory for the captured data | String | "./data"                              |
| metaDir       | Directory for the metadata      | String | "./meta"                              |
| restURL       | URL of metadata REST server     | String | "http://localhost:8080/metadata/JSON" |
| type          | Data type                       | String | "adabas"                              |

