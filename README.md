Code for the Activiti & Spring Boot webinar  (http://www.jorambarrez.be/blog/2014/09/29/webinar-on-youtube/)

First, clone the latest and greatest Activiti from https://github.com/Activiti/Activiti

On the root, execute 'mvn -Pcheck clean install -DskipTests' to get the correct dependencies. 
Go to modules/activiti-spring-boot/ and execute 'mvn clean install -DskipTests'

(they are not part of the default profile yet - will fix that soon)

Now you can import this demo project in your IDE.


