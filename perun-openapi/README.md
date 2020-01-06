# OpenAPI specification of Perun RPC API

This project contains specification of [Perun RPC API](https://perun-aai.org/documentation/technical-documentation/rpc-api/index.html) in [OpenAPI](https://swagger.io/docs/specification/about/) 3 
format and a Maven pom.xml file for generating a Java client for the API.

## Direct calls in Swagger Editor

The OpenAPI description can be opened in the on-line Swagger Editor, just click on this link:

https://editor.swagger.io/?url=https%3A%2F%2Fraw.githubusercontent.com%2FCESNET%2Fperun%2Fmaster%2Fperun-openapi%2Fopenapi.yml

In the right part of the editor, select the desired Perun server and authentication method in the “Server variables” form.
Then click on the name of the method that you want to call. Click on "Try it out", fill up needed parameters,
then click on "Execute".

## Java client

Java client library is generated by this project using the Maven plugin [openapi-generator-maven-plugin](https://github.com/OpenAPITools/openapi-generator/tree/master/modules/openapi-generator-maven-plugin).
The generated client can be used in the following way:

First specify a Maven dependency on this project:
```xml
<dependency>
	<groupId>cz.metacentrum.perun</groupId>
	<artifactId>perun-openapi</artifactId>
	<version>${perun.version}</version>
</dependency>
```

then use the class PerunRPC in your code:

```
import cz.metacentrum.perun.openapi.PerunRPC;
import cz.metacentrum.perun.openapi.PerunException;
import cz.metacentrum.perun.openapi.model.Group
...

     PerunRPC perunRPC = new PerunRPC(PerunRPC.PERUN_URL_CESNET, user, password);
     try {

        Group group = perunRPC.getGroupsManager().getGroupById(1);

     } catch (HttpClientErrorException ex) {
         throw PerunException.to(ex);
     } catch (RestClientException ex) {
        log.error("connection problem",ex);
     }
```
## Clients in other programming languages

The tool [OpenAPI Generator](https://github.com/OpenAPITools/openapi-generator) can generate clients 
in many languages, for the complete list see [Generators List](https://openapi-generator.tech/docs/generators.html).

An executable JAR file with the generator can be downloaded from Maven central repository using a command like this:
```bash
GENERATOR_VERSION=4.2.1
if [ ! -f  "openapi-generator-cli-$GENERATOR_VERSION.jar" ] ; then
  wget http://central.maven.org/maven2/org/openapitools/openapi-generator-cli/$GENERATOR_VERSION/openapi-generator-cli-$GENERATOR_VERSION.jar
fi
```

Client library can be then generated with the command
```bash
java -jar openapi-generator-cli-$GENERATOR_VERSION.jar generate \
  --input-spec https://raw.githubusercontent.com/CESNET/perun/master/perun-openapi/openapi.yml \ 
  --generator-name python 
 ```
where the value of the --generator-name option should be the name of the language from the generators list.
For detailed documentation see [generator usage](https://openapi-generator.tech/docs/usage#generate).

An example of generating Python client is available in [generate.sh](../perun-cli-python/generate.sh)  