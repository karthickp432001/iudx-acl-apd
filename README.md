[![Jenkins Build](https://img.shields.io/jenkins/build?jobUrl=https%3A%2F%2Fjenkins.iudx.io%2Fjob%2FACL-APD%2520Server(master)%2F)](https://jenkins.iudx.io/job/ACL-APD%20Server(master)/lastBuild/)
[![Jenkins Tests](https://img.shields.io/jenkins/tests?jobUrl=https%3A%2F%2Fjenkins.iudx.io%2Fjob%2FACL-APD%2520Server(master)%2F)](https://jenkins.iudx.io/job/ACL-APD%20Server(master)/lastBuild/testReport/)
[![Jenkins Coverage](https://img.shields.io/jenkins/coverage/jacoco?jobUrl=https%3A%2F%2Fjenkins.iudx.io%2Fjob%2FACL-APD%2520Server(master)%2F)](https://jenkins.iudx.io/job/ACL-APD%20Server(master)/lastBuild/jacoco/)
[![Jenkins Build](https://img.shields.io/jenkins/build?jobUrl=https%3A%2F%2Fjenkins.iudx.io%2Fjob%2FACL-APD%2520Server(master)%2F&label=integration%20tests)](https://jenkins.iudx.io/job/ACL-APD%20Server(master)/lastBuild/Integration_20Test_20Report/)
[![Jenkins Build](https://img.shields.io/jenkins/build?jobUrl=https%3A%2F%2Fjenkins.iudx.io%2Fjob%2FACL-APD%2520Server(master)%2F&label=security%20tests)](https://jenkins.iudx.io/job/ACL-APD%20Server(master)/lastBuild/zap/)


![IUDX](./docs/iudx.png)


# DX-ACL-APD-Server
Data Exchange (DX) Access Control Layer (ACL) and Access Policy Domain (APD)
is used for creating, requesting and managing policy. Provider, provider delegates could
allow the consumer, consumer delegates to access their resources by writing a policy against it.
Policies are verified by Data Exchange (DX) Authentication Authorization and Accounting Server (AAA) Server to
allow consumer, consumer delegates to access the resource.

<p align="center">
<img src="./docs/acl-apd-architecture.png">
</p>


## Features
- Allows provider, provider delegates to create, fetch, manage policies over their resources
- Allows consumers fetch policies, request access for resources by sending email notifications to the provider, provider delegates
- Emails are sent asynchronously using Vert.x SMTP Mail Client
- Integration with DX AAA Server for token introspection to serve data privately to the designated user
- Uses Vert.x, Postgres to create scalable, service mesh architecture
- Integration with auditing server for metering purposes


## API Docs
API docs are available [here](https://acl-apd.iudx.io/apis)

## Prerequisites
### External Dependencies Installation

The DX ACL-APD Server connects with various external dependencies namely:
- PostgreSQL
- RabbitMQ

Find the installation of the above along with the configurations to modify the database url, port, and associated credentials
in the appropriate sections [here](SETUP.md)

## Get Started

### Docker based
1. Install docker and docker-compose
2. Clone this repo
3. Build the images
   ` ./docker/build.sh`
4. Modify the `docker-compose.yml` file to map the config file you just created
5. Start the server in production (prod) or development (dev) mode using docker-compose
   ` docker-compose up prod `
### Maven based
1. Install java 11 and maven
2. Use the maven exec plugin based starter to start the server
   `mvn clean compile exec:java@acl-apd-server`

### JAR based
1. Install java 11 and maven
2. Set Environment variables
```
export ACL_APD_URL=https://<acl-apd-domain-name>
export LOG_LEVEL=INFO
```
3. Use maven to package the application as a JAR
   `mvn clean package -Dmaven.test.skip=true`
4. 2 JAR files would be generated in the `target/` directory
   - `iudx.iudx.apd.acl.server-cluster-0.0.1-SNAPSHOT-fat.jar` - clustered vert.x containing micrometer metrics
   - `iudx.iudx.apd.acl.server-dev-0.0.1-SNAPSHOT-fat.jar` - non-clustered vert.x and does not contain micrometer metrics


#### Running the clustered JAR
**Note**: The clustered JAR requires Zookeeper to be installed. Refer [here](https://zookeeper.apache.org/doc/r3.3.3/zookeeperStarted.html) to learn more about how to set up Zookeeper. Additionally, the `zookeepers` key in the config being used needs to be updated with the IP address/domain of the system running Zookeeper.
The JAR requires 3 runtime arguments when running:

* --config/-c : path to the config file
* --hostname/-i : the hostname for clustering
* --modules/-m : comma separated list of module names to deploy

e.g. ```java -jar target/iudx.iudx.apd.acl.server-cluster-0.0.1-SNAPSHOT-fat.jar --host $(hostname)
-c secrets/all-verticles-configs/config-dev.json -m iudx.apd.acl.server.authentication.AuthenticationVerticle, iudx.apd.acl.server.apiserver.ApiServerVerticle,
iudx.apd.acl.server.policy.PolicyVerticle, iudx.apd.acl.server.notification.NotificationVerticle, iudx.apd.acl.server.auditing.AuditingVerticle```

Use the `--help/-h` argument for more information. You may additionally append an `ACL_APD_JAVA_OPTS` environment
variable containing any Java options to pass to the application.

e.g.
```
$ export ACL_APD_JAVA_OPTS="Xmx40496m"
$ java $ACL_APD_JAVA_OPTS -jar target/iudx.iudx.apd.acl.server-cluster-0.0.1-SNAPSHOT-fat.jar ...

```


#### Running the non-clustered JAR
The JAR requires 1 runtime argument when running

* --config/-c : path to the config file

e.g. `java -Dvertx.logger-delegate-factory-class-name=io.vertx.core.logging.Log4j2LogDelegateFactory -jar target/iudx.iudx.apd.acl.server-cluster-0.0.1-SNAPSHOT-fat.jar -c secrets/all-verticles-configs/config-dev.json`

Use the `--help/-h` argument for more information. You may additionally append an `RS_JAVA_OPTS` environment variable containing any Java options to pass to the application.

e.g.
```
$ export ACL_APD_JAVA_OPTS="-Xmx1024m"
$ java ACL_APD_JAVA_OPTS -jar target/iudx.iudx.apd.acl.server-cluster-0.0.1-SNAPSHOT-fat.jar ...
```

### Testing
1. Run the server through either docker, maven or redeployer
2. Run the unit tests and generate a surefire report
   `mvn clean test-compile surefire:test surefire-report:report`
3. Reports are stored in `./target/

### Integration tests
Integration tests are through Postman/Newman whose script can be found from [here](https://github.com/datakaveri/iudx-acl-apd/tree/main/src/test/resources).
1. Install prerequisites
  - [postman](https://www.postman.com/) + [newman](https://www.npmjs.com/package/newman)
  - [newman reporter-htmlextra](https://www.npmjs.com/package/newman-reporter-htmlextra)
2. Example Postman environment can be found [here](https://github.com/datakaveri/iudx-acl-apd/blob/main/src/test/resources/IUDX-ACL-APD-APIs.postman_environment.json)
3. Run the server through either docker, maven or redeployer
4. Run the integration tests and generate the newman report
   `newman run <postman-collection-path> -e <postman-environment> --insecure -r htmlextra --reporter-htmlextra-export .`
5. Reports are stored in `./target/`

## Contributing
We follow Git Merge based workflow
1. Fork this repo
2. Create a new feature branch in your fork. Multiple features must have a hyphen separated name
3. Commit to your fork and raise a Pull Request with upstream

## License
[View License](./LICENSE)

