# OpenGraph service 
Name is subject to change.

This is very basic Open Graph scraper built with Spring 5 web reactive framework

## Features

What's all the bells and whistles this project can perform?
* Scrapping OpenGraph meta information from web pages
* Can follow 301/302 redirects
* Some additional meta information also provided (host name, mime type, title tag content)

## Running

All you need to run this service is JRE 8.x (preferably oracle).

Run with simple command:
`java -jar <jar-name>.jar`

This repository also contains Systemd unit file `systemd/og.service` (you may want to change paths and uid/gid 
according to your setup).

Https is recommended to be configured with reverse proxy (nginx, etc).

## Setup / Basic Usage

### Configuration

It's advised to use spring boot application.properties to configure application.

Service is ready to use without additional configuration, but you can 

Notable properties:
* `server.port` - which port to use
* `logging.*` - logging configuration.
More information [here](https://docs.spring.io/spring-boot/docs/current/reference/html/boot-features-logging.html).
* `og.maxBodySize` - maximum body size for received page body (default 8mb)
* `og.maxSupportedRedirects` - maximum allowed number of consequent redirects to handle
* For CORS configuration look [CORS support](#cors-support)
* todo: cache config (not implemented yet #1)

### CORS support

At the moment, CORS support is advised to be configured via reverse proxy (nginx, etc). Native configurable CORS support will be 
implemented in future. See #3

### Usage

You need to make http POST request to `/fetch` with json body like:
```json
{
    "url": "https://google.com"
}
```

If error occurred during fetch, response will contain single field `error` with error reason.

Assuming service is running on `localhost` with port `8080`, you can make request via curl like:
```
curl -XPOST localhost:8080/fetch -H "Content-Type: application/json" -H "Accept: application/json" -d '{"url": "https://google.com"}'
```

### Building

All you need to build this project is Maven and JDK 8.x:
```
mvn package
```

After build is complete you can get jar in `target` directory.

## Licensing

"The code in this project is licensed under MIT license."