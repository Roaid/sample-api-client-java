sample-api-client-java
======================

sample api client using Java language

Running on eclipse
==================

You need to have apache-maven and M2\_REPO in eclipse's classpath already

```
$ git clone git@github.com:vogelito/sample-api-client-java.git
$ cd sample-api-client-java
$ mvn eclipse:eclipse
```

Then you need to cp ```keys.conf_sample``` to ```keys.conf``` and insert the appropriate values for your API_KEY and PRIVATE_KEY

In eclipse: File -> Import -> General -> Existing Projects into Workspace -> Select the sample-api-client-java directory -> Finish