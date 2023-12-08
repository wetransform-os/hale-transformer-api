# hale-transformer-api

Spring Boot application that runs hale transformations based on requests read from a AMQP message queue.
The message queue needs to be provided externally, e.g. by a RabbitMQ instance.

## Build

To build the application, run

    ./gradlew build

To start an instance, run

    ./gradlew bootRun

## RabbitMQ

To start a local RabbitMQ instance that can be used for debugging purposes, run

    docker compose up

in the root directory of this project.

The local instance can be also used to send messages to the queue that are then
processed by the application. When the application is running, an example message
like the following can be sent to the queue:

```bash
$ docker exec -ti hale-transformer-api-rabbitmq-1 bash
root@123456789abc:/# rabbitmqadmin publish \
> exchange=hale-transformer-exchange \
> routing_key=hale.transformation.foo \
> properties='{"content_type":"application/json"}' payload='{"projectUrl": "http://example.org/proj", "sourceDataUrl": "http://example.org/data"}'
Message published
root@123456789abc:/#
```
