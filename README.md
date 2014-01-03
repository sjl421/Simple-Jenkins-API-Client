Simple-Jenkins-API-Client
=========================

An simple Jenkins API client written in Java. 

## Usage

Connect to Jenkins server without credentials
```java
client = new JenkinsClient("localhost", 8080);
```

Connect to Jenkins server with credentials
```java
client = new JenkinsClient("localhost", 8080, "admin", "password");
```

Connect to Jenkins server with credentials and HTTPS
```java
client = new JenkinsClient("localhost", 443, "admin", "password", true);
```

Trigger a job
```java
client.job().build("simple_job");
```

Trigger a parameterized job
```java
Map<String, String> params = new HashMap<String, String>();
params.put("param1", "value1");
params.put("param2", "value2");
client.job().build("simple_params_job", params);
```
